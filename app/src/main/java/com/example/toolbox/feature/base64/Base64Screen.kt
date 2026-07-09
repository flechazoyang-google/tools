package com.example.toolbox.feature.base64

import android.util.Base64
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.toolbox.core.components.TopBar
import java.nio.charset.StandardCharsets

@Composable
fun Base64Screen() {
    var input by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var isEncoding by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(title = "Base64 编解码")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = input,
                onValueChange = { input = it; errorMessage = null; output = "" },
                label = { Text(if (isEncoding) "明文" else "Base64 编码") },
                placeholder = { Text(if (isEncoding) "输入要编码的文本" else "输入要解码的 Base64 字符串") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(12.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        if (input.isBlank()) return@Button
                        try {
                            output = if (isEncoding) {
                                Base64.encodeToString(
                                    input.toByteArray(StandardCharsets.UTF_8),
                                    Base64.NO_WRAP
                                )
                            } else {
                                String(
                                    Base64.decode(input, Base64.DEFAULT),
                                    StandardCharsets.UTF_8
                                )
                            }
                            errorMessage = null
                        } catch (e: Exception) {
                            errorMessage = if (isEncoding) "编码失败" else "解码失败：无效的 Base64 字符串"
                            output = ""
                        }
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(if (isEncoding) "编码" else "解码")
                }

                OutlinedButton(
                    onClick = {
                        isEncoding = !isEncoding
                        input = ""
                        output = ""
                        errorMessage = null
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(if (isEncoding) "解码" else "编码")
                }
            }

            errorMessage?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (output.isNotEmpty()) {
                OutlinedTextField(
                    value = output,
                    onValueChange = {},
                    label = { Text(if (isEncoding) "Base64 编码" else "解码结果") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(12.dp),
                    readOnly = true,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { clipboard.setText(AnnotatedString(output)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("复制结果")
                    }

                    OutlinedButton(
                        onClick = { input = output; output = ""; errorMessage = null },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text("作为输入")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
