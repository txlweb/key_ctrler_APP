package com.idlike.kctrl.mgr

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.button.MaterialButton
import java.io.File
import java.io.FileOutputStream

class UploadFragment : Fragment() {
    
    private lateinit var scriptNameEditText: TextInputEditText
    private lateinit var authorNameEditText: TextInputEditText
    private lateinit var descriptionEditText: TextInputEditText
    private lateinit var selectFileButton: MaterialButton
    private lateinit var uploadButton: MaterialButton
    
    private var selectedFileUri: Uri? = null
    private var selectedFileName: String = ""
    
    companion object {
        private const val PICK_FILE_REQUEST = 1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_upload, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initViews(view)
        setupListeners()
    }

    private fun initViews(view: View) {
        scriptNameEditText = view.findViewById(R.id.scriptNameEditText)
        authorNameEditText = view.findViewById(R.id.authorNameEditText)
        descriptionEditText = view.findViewById(R.id.descriptionEditText)
        selectFileButton = view.findViewById(R.id.selectFileButton)
        uploadButton = view.findViewById(R.id.uploadButton)
    }

    private fun setupListeners() {
        selectFileButton.setOnClickListener {
            selectFile()
        }
        
        uploadButton.setOnClickListener {
            uploadFile()
        }
    }

    private fun selectFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, PICK_FILE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == PICK_FILE_REQUEST && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                selectedFileUri = uri
                selectedFileName = getFileName(uri)
                selectFileButton.text = "已选择: $selectedFileName"
            }
        }
    }

    private fun getFileName(uri: Uri): String {
        var name = ""
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = it.getString(nameIndex)
                }
            }
        }
        return name
    }

    private fun uploadFile() {
        val scriptName = scriptNameEditText.text.toString().trim()
        val authorName = authorNameEditText.text.toString().trim()
        val description = descriptionEditText.text.toString().trim()

        if (scriptName.isEmpty()) {
            scriptNameEditText.error = "请输入脚本名称"
            return
        }

        if (authorName.isEmpty()) {
            authorNameEditText.error = "请输入作者名称"
            return
        }

        if (containsIdlike(authorName)) {
            authorNameEditText.error = "作者名称不能包含idlike的任何形式"
            return
        }

        if (selectedFileUri == null) {
            Toast.makeText(requireContext(), "请先选择文件", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            saveFileToInternalStorage()
            Toast.makeText(requireContext(), "上传成功！文件已保存到应用内部目录", Toast.LENGTH_SHORT).show()
            clearForm()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "上传失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun containsIdlike(text: String): Boolean {
        val lowerText = text.lowercase()
        return lowerText.contains("idlike") || 
               lowerText.contains("id-like") || 
               lowerText.contains("id like") ||
               lowerText.contains("id_like")
    }

    private fun saveFileToInternalStorage() {
        selectedFileUri?.let { uri ->
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val internalStorageDir = requireContext().getDir("uploads", 0)
            val file = File(internalStorageDir, selectedFileName)
            
            inputStream?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private fun clearForm() {
        scriptNameEditText.text?.clear()
        authorNameEditText.text?.clear()
        descriptionEditText.text?.clear()
        selectedFileUri = null
        selectedFileName = ""
        selectFileButton.text = "选择文件"
    }
}