package com.clozed.code

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clozed.code.data.Block
import com.clozed.code.data.ChatItem
import com.clozed.code.data.PermissionReq
import com.clozed.code.data.ProviderDef
import com.clozed.code.data.Screen
import com.clozed.code.data.SessionInfo
import com.clozed.code.engine.Catalog
import com.clozed.code.engine.Commands
import com.clozed.code.engine.CustomCommand
import com.clozed.code.engine.BackendHost
import com.clozed.code.engine.Llm
import com.clozed.code.engine.LlmException
import com.clozed.code.engine.LlmRequest
import com.clozed.code.engine.LocalBackend
import com.clozed.code.engine.MPart
import com.clozed.code.engine.McpManager
import com.clozed.code.engine.McpServer
import com.clozed.code.engine.PermReq
import com.clozed.code.engine.PermRules
import com.clozed.code.engine.PermissionDecision
import com.clozed.code.engine.PromptBuilder
import com.clozed.code.engine.ProviderStore
import com.clozed.code.engine.Sess
import com.clozed.code.engine.SessMsg
import com.clozed.code.engine.SessionStore
import com.clozed.code.engine.Skills
import com.clozed.code.engine.StorageAccess
import com.clozed.code.engine.StoredProvider
import com.clozed.code.engine.Todo
import com.clozed.code.engine.ToolCtx
import com.clozed.code.engine.UserPrefs
import com.clozed.code.engine.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/** Immutable snapshot the UI renders; the ViewModel updates its fields. */
data class UiState(
    val screen: Screen = Screen.Chat,
    val running: Boolean = false,
    val state: String = "",
    val provider: String = "anthropic",
    val model: String = "claude-sonnet-4-5",
    val agent: String = "build",
    val project: String? = null,
)

/** A question the agent asked and is waiting on. */
data class QuestionReq(val id: String, val question: String, val options: List<String>)

/**
 * The single source of truth for the whole app: provider catalog, chat
 * transcript, session store, skills, MCP servers and the running agent turn.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = UserPrefs(app)
    internal val store = ProviderStore(app)
    internal val permRules = PermRules(app)
    internal val sessions = SessionStore(app)
    val mcp = McpManager(app)

    var catalog: List<ProviderDef> by mutableStateOf(emptyList()); private set
    var providers: List<StoredProvider> by mutableStateOf(emptyList()); private set
    var skills: List<Skills.Skill> by mutableStateOf(emptyList()); private set
    var customCommands: List<CustomCommand> by mutableStateOf(emptyList()); private set
    var mcpServers: List<McpServer> by mutableStateOf(emptyList()); private set
    var sessionList: List<SessionInfo> by mutableStateOf(emptyList()); private set

    val items = mutableStateListOf<ChatItem>()
    val todos = mutableStateListOf<Todo>()

    var ui: UiState by mutableStateOf(UiState()); private set
    var input: String by mutableStateOf("")
    var pending: PermissionReq? by mutableStateOf(null)
    var pendingQuestion: QuestionReq? by mutableStateOf(null)
    var banner: String? by mutableStateOf(null)
    var autoSwitchNotice: String? by mutableStateOf(null)
    var activeSession: String by mutableStateOf(""); private set

    private var sess: Sess = Sess(id = sessions.newId())

    init {
        prefs.projectFolder?.let { Workspace.setRoot(File(it)) } ?: Workspace.setRoot(Workspace.defaultProjects())
        refresh()
    }

    // ---- loading ---------------------------------------------------------

    fun refresh() {
        val assets = getApplication<Application>().assets
        val text = runCatching { assets.open("providers.json").bufferedReader().use { it.readText() } }.getOrNull()
        catalog = Catalog.load(text)
        providers = store.merged(catalog)
        loadSkills()
        loadCustomCommands()
        sessionList = sessions.list()
        mcpServers = mcp.servers()
        ui = ui.copy(
            provider = store.selectedProvider(),
            model = store.selectedModel(),
            agent = store.selectedAgent(),
            project = prefs.projectFolder,
        )
    }

    private fun loadSkills() {
        val assets = getApplication<Application>().assets
        val ids = runCatching { assets.list("skills")?.toList() }.getOrNull().orEmpty()
        skills = ids.mapNotNull { id ->
            runCatching {
                assets.open("skills/$id/SKILL.md").bufferedReader().use { Skills.parse(id, it.readText()) }
            }.getOrNull()
        }
    }

    private fun loadCustomCommands() {
        val root = Workspace.root ?: return
        val dirs = listOf(File(root, ".clozed/command"), File(root, ".clozed/commands"),
            File(root, ".opencode/command"), File(root, ".opencode/commands"))
        customCommands = dirs.filter { it.isDirectory }.flatMap { d ->
            d.listFiles { f -> f.extension == "md" }?.mapNotNull { f ->
                runCatching {
                    val body = f.readText()
                    CustomCommand(f.nameWithoutExtension, firstLine(body), body)
                }.getOrNull()
            }.orEmpty()
        }
    }

    private fun firstLine(md: String): String =
        md.lineSequence().firstOrNull { it.isNotBlank() && !it.startsWith("---") }?.trim().orEmpty()

    // ---- selection --------------------------------------------------------

    fun modelsFor(providerId: String): List<String> =
        providers.firstOrNull { it.id == providerId }?.models.orEmpty()

    fun selectModel(providerId: String, modelId: String) {
        store.setSelection(providerId, modelId)
        ui = ui.copy(provider = providerId, model = modelId)
    }

    fun selectAgent(agent: String) {
        prefs.agent = agent
        ui = ui.copy(agent = agent)
    }

    fun setScreen(screen: Screen) { ui = ui.copy(screen = screen) }

    fun setProject(path: String) {
        prefs.projectFolder = path
        Workspace.setRoot(File(path))
        loadCustomCommands()
        ui = ui.copy(project = path)
    }

    // ---- provider management ---------------------------------------------

    /** Enables or disables a provider; enabling one also selects it. */
    fun toggleProvider(p: StoredProvider) {
        val updated = providers.map { if (it.id == p.id) it.copy(enabled = !it.enabled) else it }
        providers = updated
        store.save(updated)
        if (!p.enabled) selectModel(p.id, p.models.firstOrNull() ?: store.selectedModel())
    }

    fun addKey(p: StoredProvider, key: String) = saveKeys(p, p.keys + key)

    fun removeKey(p: StoredProvider, index: Int) =
        saveKeys(p, p.keys.filterIndexed { i, _ -> i != index })

    private fun saveKeys(p: StoredProvider, keys: List<String>) {
        val updated = providers.map { if (it.id == p.id) it.copy(keys = keys) else it }
        providers = updated
        store.save(updated)
    }

    // ---- MCP --------------------------------------------------------------

    fun addMcp(server: McpServer) {
        mcp.add(server)
        mcpServers = mcp.servers()
        viewModelScope.launch { runCatching { mcp.refreshAll() } }
    }

    fun toggleMcp(server: McpServer) {
        mcp.update(server.copy(enabled = !server.enabled))
        mcpServers = mcp.servers()
        viewModelScope.launch { runCatching { mcp.refreshAll() } }
    }

    // ---- permissions ------------------------------------------------------

    fun permRulesList(): List<com.clozed.code.engine.PermRule> = permRules.rules()
    fun addPermRule(pattern: String, action: String) =
        permRules.setRules(permRules.rules() + com.clozed.code.engine.PermRule(pattern, action))
    fun removePermRule(index: Int) =
        permRules.setRules(permRules.rules().filterIndexed { i, _ -> i != index })
    fun autoApproveAll(): Boolean = permRules.alwaysAutoApprove()
    fun setAutoApproveAll(v: Boolean) = permRules.setAlwaysAutoApprove(v)

    // ---- storage access ---------------------------------------------------

    /** True when the agent can read and write files on shared storage. */
    fun hasStorageAccess(): Boolean = StorageAccess.hasAccess(getApplication())

    /** Opens the system "All files access" screen (Android 11+). */
    fun requestStorageAccess(): Boolean = StorageAccess.openSettings(getApplication())

    /** The runtime permission to request on Android 10 and below. */
    fun storageRuntimePermission(): String = StorageAccess.legacyPermissionName()

    // ---- simple settings toggles (used by Advanced) ----------------------

    fun effort(): String? = prefs.reasoningEffort
    fun setEffort(v: String?) {
        prefs.reasoningEffort = v
    }

    fun backgroundWork(): Boolean = prefs.backgroundWork
    fun setBackgroundWork(v: Boolean) { prefs.backgroundWork = v }

    fun agentAlerts(): Boolean = prefs.agentAlerts
    fun setAgentAlerts(v: Boolean) { prefs.agentAlerts = v }

    fun voiceAlerts(): Boolean = prefs.voiceAlerts
    fun setVoiceAlerts(v: Boolean) { prefs.voiceAlerts = v }

    // ---- provider backup --------------------------------------------------

    fun exportBackup(): String = store.export()
    fun importBackup(text: String): Int {
        val n = store.import(text)
        refresh()
        return n
    }

    fun client(): StoredProvider? = providers.firstOrNull { it.id == ui.provider }
        ?: providers.firstOrNull { it.enabled }

    fun providerDef(id: String): ProviderDef? = catalog.firstOrNull { it.id == id }

    // ---- running a turn ---------------------------------------------------

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || ui.running) return
        if (t.startsWith("/")) { runCommand(t); return }
        items.add(ChatItem.User("u${System.currentTimeMillis()}", t))
        input = ""
        startTurn(t, session=false)
    }

    fun answerPermission(decision: PermissionDecision, remember: Boolean = false) {
        val req = pending ?: return
        if (remember) {
            val action = if (decision == PermissionDecision.Deny) "deny" else "allow"
            permRules.setRules(permRules.rules() + com.clozed.code.engine.PermRule(req.detail.ifBlank { req.title }, action))
        }
        if (decision == PermissionDecision.AllowNext) permRules.allowNext(PermReq(req.kind, req.title, req.detail))
        pending = null
        pendingDecision?.invoke(decision)
        pendingDecision = null
    }

    fun answerQuestion(answer: String) {
        if (pendingQuestion == null) return
        items.add(ChatItem.User("u${System.currentTimeMillis()}", answer))
        pendingQuestion = null
        questionReply?.invoke(answer)
        questionReply = null
    }

    fun interrupt() {
        backend?.interrupt()
        banner = "Stopped."
    }

    fun newSession() {
        sess = Sess(id = sessions.newId())
        activeSession = sess.id
        items.clear()
        todos.clear()
    }

    fun openSession(id: String) {
        val s = sessions.load(id) ?: return
        sess = s
        activeSession = id
        items.clear()
        todos.clear()
        for (m in s.messages) {
            when (m.role) {
                "user" -> items.add(ChatItem.User("u${m.ts}", m.text))
                "tool" -> {}
                else -> items.add(ChatItem.Assistant("a${m.ts}", listOf(Block.Text(m.text))))
            }
        }
        ui = ui.copy(screen = Screen.Chat)
    }

    fun deleteSession(id: String) {
        sessions.delete(id)
        sessionList = sessions.list()
    }

    private var backend: com.clozed.code.engine.LocalBackend? = null
    private var pendingDecision: ((PermissionDecision) -> Unit)? = null
    private var questionReply: ((String) -> Unit)? = null
    private var freeModelUsed = false

    private fun runCommand(raw: String) {
        val cmd = Commands.parse(raw) ?: return
        val s = getApplication<Application>()
        when (cmd.name) {
            "/help" -> notice(Commands.help(customCommands))
            "/new" -> newSession()
            "/clear" -> { items.clear(); todos.clear() }
            "/sessions" -> setScreen(Screen.Sessions)
            "/connect" -> setScreen(Screen.Settings)
            "/folder" -> setScreen(Screen.FolderPicker)
            "/model" -> {
                if (cmd.arg.isBlank()) setScreen(Screen.Settings)
                else { selectModel(ui.provider, cmd.arg); notice("Model set to ${cmd.arg}.") }
            }
            "/models" -> notice(modelsFor(ui.provider).take(100).joinToString("\n").ifBlank { "No models." })
            "/effort" -> { prefs.reasoningEffort = cmd.arg.ifBlank { null }; notice("Reasoning effort: ${cmd.arg.ifBlank { "default" }}") }
            "/undo" -> undo()
            "/compact" -> notice("Nothing to compact in this session yet.")
            "/port" -> setScreen(Screen.FolderPicker)
            "/init" -> startTurn("Explore this project and create an AGENTS.md that describes its structure, build and conventions.", session=false)
            else -> {
                val custom = customCommands.firstOrNull { cmd.name == "/${it.name}" }
                if (custom != null) startTurn(custom.expand(cmd.arg), session=false)
                else notice("Unknown command: ${cmd.name}. Try /help.", warn = true)
            }
        }
    }

    private fun undo() {
        val set = backend?.undoStack?.removeLastOrNull()
        notice(set?.undo() ?: "Nothing to undo.")
    }

    private fun notice(text: String, warn: Boolean = false) {
        items.add(ChatItem.Notice("n${System.currentTimeMillis()}", text, if (warn) ChatItem.Notice.Kind.Warn else ChatItem.Notice.Kind.Info))
    }

    // ---- turn wiring ------------------------------------------------------

    private fun startTurn(prompt: String, session: Boolean) {
        if (!hasStorageAccess()) {
            notice("Grant storage access so the agent can read and write your project folder.", warn = true)
            return
        }
        val client = client()
        if (client == null || client.keys.none { it.isNotBlank() }) {
            notice("Add a provider and API key first (Settings, or the Connect tab).", warn = true)
            return
        }
        val def = providerDef(client.id) ?: catalog.firstOrNull()
        if (def == null) { notice("Provider catalog is missing.", warn = true); return }
        val model = pickModel(client, def)
        sess = sess.withTitleIfEmpty(prompt)
        activeSession = sess.id
        ui = ui.copy(running = true)
        state = "thinking"

        val host = Host(client, def, model)
        val ctx = buildCtx()
        val backend = LocalBackend(Llm(), host, ctx, onRequestError = { e -> rotateKey(client, e) })
        this.backend = backend
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { backend.run(prompt) }
            } finally {
                ui = ui.copy(running = false)
                state = ""
                persist()
            }
        }
    }

    private fun pickModel(client: StoredProvider, def: ProviderDef): String {
        val selected = store.selectedModel()
        if (client.id == store.selectedProvider() && client.models.contains(selected)) return selected
        return client.models.firstOrNull() ?: selected
    }

    private fun buildCtx(): ToolCtx = ToolCtx(
        root = Workspace.root,
        permission = { req ->
            val d = permRules.decide(req)
            if (d == PermissionDecision.Ask) suspendPermission(req) else d
        },
        askQuestion = { q, opts -> askQuestionInternal(q, opts) },
        runSubagent = { agent, prompt -> runSubagentInternal(agent, prompt) },
        loadSkill = { id -> skills.firstOrNull { it.id == id }?.let { "# ${it.name}\n\n${it.body}" } },
    )

    private suspend fun suspendPermission(req: PermReq): PermissionDecision =
        suspendCancellableCoroutine { cont ->
            pending = PermissionReq("p${System.currentTimeMillis()}", req.title, req.detail, req.kind)
            pendingDecision = { d -> if (cont.isActive) cont.resume(d) }
        }

    private suspend fun askQuestionInternal(q: String, options: List<String>): String =
        suspendCancellableCoroutine { cont ->
            pendingQuestion = QuestionReq("q${System.currentTimeMillis()}", q, options)
            questionReply = { a -> if (cont.isActive) cont.resume(a) }
        }

    private suspend fun runSubagentInternal(agent: String, prompt: String): String = withContext(Dispatchers.IO) {
        try {
            val client = client() ?: return@withContext "No provider configured."
            val def = providerDef(client.id) ?: return@withContext "No provider catalog."
            val system = PromptBuilder.system(getApplication<Application>().assets, store.selectedModel(), agent, Workspace.root, null)
            val req = LlmRequest(def, store.selectedModel(), client.keys.firstOrNull().orEmpty(), system,
                listOf(com.clozed.code.engine.LlmTurn.User(prompt)))
            val acc = Llm().stream(req) {}
            acc.text.toString().ifBlank { "(subagent returned nothing)" }
        } catch (e: Exception) {
            "Subagent failed: ${e.message}"
        }
    }

    private fun rotateKey(client: StoredProvider, e: LlmException): Boolean {
        if (e.code != 401 && e.code != 402 && e.code != 429) return false
        val idx = client.keys.indexOfFirst { it.isNotBlank() }
        if (idx < 0 || idx + 1 >= client.keys.size) return false
        val rotated = client.keys.filterIndexed { i, _ -> i != idx }
        providers = providers.map { if (it.id == client.id) it.copy(keys = rotated) else it }
        store.save(providers)
        autoSwitchNotice = "Key ${idx + 1} failed (${e.message}). Trying the next key."
        return true
    }

    private fun persist() {
        val msgs = ArrayList<SessMsg>()
        for (it in items) when (it) {
            is ChatItem.User -> msgs.add(SessMsg("user", it.text))
            is ChatItem.Assistant -> msgs.add(SessMsg("assistant", it.blocks.filterIsInstance<Block.Text>().joinToString("\n") { b -> b.text }))
            else -> {}
        }
        sess = sess.copy(
            directory = Workspace.root?.path, agent = ui.agent, model = store.selectedModel(),
            provider = ui.provider, messages = msgs,
        )
        sessions.save(sess)
        sessionList = sessions.list()
    }

    /** BackendHost writing into Compose state; the UI renders [items]. */
    private inner class Host(
        val client: StoredProvider,
        val def: ProviderDef,
        val model: String,
    ) : BackendHost {
        override val systemPrompt: String
            get() = PromptBuilder.system(getApplication<Application>().assets, model, ui.agent, Workspace.root, projectSystem())

        override val request: LlmRequest
            get() = LlmRequest(def, model, client.keys.firstOrNull().orEmpty(), systemPrompt, sess.toTurns())

        override fun onText(text: String) = appendText(text)
        override fun onReasoning(text: String) = appendBlock(Block.Reasoning(text))
        override fun onNotice(text: String, kind: String) = notice(text, kind == "warn" || kind == "error")
        override fun onState(s: String) { state = s; ui = ui.copy(state = s) }

        override fun onToolStart(callId: String, name: String, input: String) =
            appendBlock(Block.Tool(callId, name, input, "", running = true))

        override fun onToolEnd(callId: String, output: String, ok: Boolean) = finishTool(callId, output, ok)

        override fun sessionTurn(assistant: List<MPart>) {
            for (p in assistant) if (p is MPart.Reasoning && p.text.isNotBlank()) appendBlock(Block.Reasoning(p.text))
            val text = assistant.filterIsInstance<MPart.Text>().joinToString("") { it.text }
            if (text.isNotBlank()) appendBlock(Block.Text(text))
            sess = sess.copy(messages = sess.messages + SessMsg("assistant", text))
        }

        override fun sessionResult(callId: String, name: String, content: String) {
            sess = sess.copy(messages = sess.messages + SessMsg("tool", content, name, callId))
        }

        override suspend fun awaitPermission(request: PermReq): PermissionDecision {
            val d = permRules.decide(request)
            return if (d == PermissionDecision.Ask) suspendPermission(request) else d
        }
        override suspend fun askQuestion(question: String, options: List<String>): String = askQuestionInternal(question, options)
        override suspend fun spawnSubagent(agent: String, prompt: String): String = runSubagentInternal(agent, prompt)
    }

    private fun projectSystem(): String? {
        val root = Workspace.root ?: return null
        val cfg = File(root, "clozed.json").takeIf { it.isFile } ?: File(root, "opencode.json").takeIf { it.isFile }
        return cfg?.let { runCatching { it.readText() }.getOrNull() }
    }

    private var state: String = ""

    private fun appendText(text: String) {
        val last = items.lastOrNull()
        if (last is ChatItem.Assistant) {
            val blocks = last.blocks.toMutableList()
            val tail = blocks.lastOrNull()
            if (tail is Block.Text) blocks[blocks.lastIndex] = Block.Text(tail.text + text)
            else blocks.add(Block.Text(text))
            items[items.lastIndex] = last.copy(blocks = blocks)
        } else {
            items.add(ChatItem.Assistant("a${System.currentTimeMillis()}", listOf(Block.Text(text))))
        }
    }

    private fun appendBlock(block: Block) {
        val last = items.lastOrNull()
        if (last is ChatItem.Assistant) {
            items[items.lastIndex] = last.copy(blocks = last.blocks + block)
        } else {
            items.add(ChatItem.Assistant("a${System.currentTimeMillis()}", listOf(block)))
        }
    }

    private fun finishTool(callId: String, output: String, ok: Boolean) {
        for (i in items.indices.reversed()) {
            val item = items[i]
            if (item is ChatItem.Assistant) {
                val idx = item.blocks.indexOfLast { it is Block.Tool && it.id == callId }
                if (idx >= 0) {
                    val blocks = item.blocks.toMutableList()
                    blocks[idx] = (blocks[idx] as Block.Tool).finish(output, ok)
                    items[i] = item.copy(blocks = blocks)
                    return
                }
            }
        }
    }
}
