package me.jameshunt.merkle

import org.apache.commons.codec.digest.DigestUtils
import java.io.File

fun main() {
    walkFileDirectory()
}

fun walkFileDirectory(): FolderNode {

    val rootFile = File("src/main/resources")
    val rootNode = FolderNode(rootFile.absolutePath, mutableListOf())

    rootFile
        .also { println(it.absolutePath) }
        .walk()
        .onEnter { file ->
            val parentFolder = rootNode.findFolder(file.absolutePath)
            parentFolder.children.add(FolderNode(file.absolutePath, mutableListOf()))

            true
        }
        .onFail { file, ioException -> ioException.printStackTrace() }
        .forEach {
            if (it.isFile) {
                val parentFolder = rootNode.findFolder(it.absolutePath)
                parentFolder.children.add(FileNode(it.absolutePath, it.getHash()))
            }
        }

    return rootNode.also { println(it) }
}

fun FolderNode.findFolder(path: String): FolderNode {
    val childFolders = this.children.mapNotNull { it as? FolderNode }

    childFolders.forEach {
        if (it.path == path) {
            return it
        }
    }

    childFolders.forEach {
        val childFolder = it.findFolder(path)
        if(childFolder.path == path) {
            return childFolder
        }
    }

    return this
}

fun File.getHash(): String = this.inputStream().use { inputStream ->
    DigestUtils.md5Hex(DigestUtils.md5Hex(inputStream) + DigestUtils.md5(this.absolutePath))
}

class Tree {
    val root: FolderNode = TODO()

}

interface Node {
    val path: String
    val hash: String
}

data class FileNode(
    override val path: String,
    override val hash: String
) : Node

data class FolderNode(
    override val path: String,
    val children: MutableList<Node>
) : Node {
    override val hash: String
        get() = children
            .joinToString("") { it.hash }
            .let { DigestUtils.md5Hex(path + it) }
}
