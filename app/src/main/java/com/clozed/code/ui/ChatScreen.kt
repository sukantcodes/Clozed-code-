package com.clozed.code.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.ui.unit.dp
import com.clozed.code.AppViewModel
import com.clozed.code.data.Block
import com.clozed.code.data.ChatItem
import com.clozed.code.engine.PermissionDecision
import com.clozed.code.ui.theme.Brand
import com.clozed.code.ui.theme.ErrorRed
import com.clozed.code.ui.theme.InfoBlue
import com.clozed.code.ui.theme.OnBrand
import com.clozed.code.ui.theme.ReasonPurple
import com.clozed.code.ui.theme.Surface1
import com.clozed.code.ui.theme.Surface2
import com.clozed.code.ui.theme.TextFaint
import com.clozed.code.ui.theme.TextLo
import com.clozed.code.ui.theme.ToolSlate
import com.clozed.code.ui.theme.WarnAmber

/** The main chat transcript plus the composer and permission prompts. */
@Composable
fun ChatScreen(vm: AppViewModel) {
    val listState = rememberLazyListState()
    LaunchedEffect(vm.items.size) {
        if (vm.items.isNotEmpty()) listState.animateScrollToItem(vm.items.lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(vm.items, key = { it.id }) { item -> ChatRow(item) }
        }

        vm.pendingQuestion?.let { q ->
            QuestionCard(q.question, q.options) { vm.answerQuestion(it) }
        }
        vm.pending?.let { p ->
            PermRow(
                title = p.title,
                detail = p.detail,
                options = p.options,
            ) { label ->
                vm.answerPermission(label.toDecision(), remember = label == "Always")
            }
        }

        vm.banner?.let { b ->
            Banner(b, WarnAmber)
        }

        Composer(vm)

        vm.todos.takeIf { it.isNotEmpty() }?.let { todos ->
            Text(
                todos.joinToString("  ·  ") { "${if (it.status == "completed") "✓" else "•"} ${it.content}" },
                style = MaterialTheme.typography.labelSmall,
                color = TextFaint,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

private fun String.toDecision(): PermissionDecision = when (this) {
    "Deny" -> PermissionDecision.Deny
    "Always" -> PermissionDecision.AllowAlways
    "Allow next" -> PermissionDecision.AllowNext
    else -> PermissionDecision.AllowOnce
}

@Composable
private fun ChatRow(item: ChatItem) {
    when (item) {
        is ChatItem.User -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .fillMaxWidth(0.9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface2)
                    .padding(12.dp),
            ) { Text(item.text, style = MaterialTheme.typography.bodyLarge) }
        }

        is ChatItem.Assistant -> Column(Modifier.fillMaxWidth()) {
            for (block in item.blocks) BlockView(block)
        }

        is ChatItem.Shell -> ToolCard("shell", item.command, item.output, running = false, ok = true)

        is ChatItem.Notice -> {
            val color = when (item.kind) {
                ChatItem.Notice.Kind.Error -> ErrorRed
                ChatItem.Notice.Kind.Warn -> WarnAmber
                else -> InfoBlue
            }
            Text(item.text, color = color, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun BlockView(block: Block) {
    when (block) {
        is Block.Text -> Markdown(block.text)
        is Block.Reasoning -> Text(
            block.text,
            color = ReasonPurple,
            style = MaterialTheme.typography.bodySmall,
        )
        is Block.Tool -> ToolCard(block.name, block.input, block.output, block.running, block.ok)
    }
}

@Composable
private fun QuestionCard(question: String, options: List<String>, onAnswer: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(8.dp).clip(RoundedCornerShape(12.dp)).background(Surface2).padding(12.dp),
    ) {
        Text(question, style = MaterialTheme.typography.titleMedium, color = InfoBlue)
        Spacer(Modifier.size(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (o in options) {
                Button(onClick = { onAnswer(o) }, modifier = Modifier.fillMaxWidth()) { Text(o) }
            }
        }
    }
}

@Composable
private fun Composer(vm: AppViewModel) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Surface1)
            .padding(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = vm.input,
            onValueChange = { vm.input = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Ask Clozed Code, or /help", color = TextFaint) },
            maxLines = 6,
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.width(8.dp))
        if (vm.ui.running) {
            IconButton(onClick = { vm.interrupt() }) {
                Icon(Icons.Filled.Close, contentDescription = "Stop", tint = ErrorRed)
            }
        } else {
            val enabled = vm.input.isNotBlank()
            IconButton(
                onClick = { if (enabled) vm.send(vm.input) },
                enabled = enabled,
            ) {
                Icon(Icons.Filled.Send, contentDescription = "Send", tint = Brand)
            }
        }
    }
}
