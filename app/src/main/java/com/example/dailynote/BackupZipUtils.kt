package com.example.dailynote

import java.io.File
import java.io.OutputStream
import net.lingala.zip4j.io.inputstream.ZipInputStream
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod

object BackupZipUtils {

    fun writeEncryptedZip(dbFile: File, outputStream: OutputStream, password: String) {
        require(password.isNotBlank()) { "备份密码不能为空" }

        val parameters = ZipParameters().apply {
            fileNameInZip = dbFile.name
            compressionMethod = CompressionMethod.DEFLATE
            compressionLevel = CompressionLevel.NORMAL
            isEncryptFiles = true
            encryptionMethod = EncryptionMethod.AES
            aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
        }

        ZipOutputStream(outputStream, password.toCharArray()).use { zipOutput ->
            zipOutput.putNextEntry(parameters)
            dbFile.inputStream().use { input ->
                input.copyTo(zipOutput)
            }
            zipOutput.closeEntry()
        }
    }

    fun extractEncryptedZip(zipFile: File, targetFile: File, password: String) {
        require(password.isNotBlank()) { "备份密码不能为空" }

        ZipInputStream(zipFile.inputStream(), password.toCharArray()).use { zipInput ->
            var entry = zipInput.nextEntry
            while (entry != null && entry.isDirectory) {
                entry = zipInput.nextEntry
            }

            if (entry == null) {
                error("备份文件中未包含数据库文件")
            }

            targetFile.outputStream().use { output ->
                zipInput.copyTo(output)
            }
        }
    }
}
