package me.jameshunt.merkle

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.commons.codec.digest.DigestUtils
import java.io.File

fun main() {
    val rootFile = File("src/main/resources")
    val tree = Tree(rootFile)
    println(tree.isSame(Tree(rootFile)))
    println(tree.root)

    ObjectMapper().writeValueAsString(tree).let { println(it) }
}

class Tree(private val folder: File) {
    val root: FolderNode by lazy { folder.buildTree() }

    fun isSame(other: Tree): Boolean = root.hash == other.root.hash

    private fun File.buildTree(): FolderNode {
        val rootNode = FolderNode(this.parentFile.absolutePath, mutableListOf())

        this
            .walk()
            .onEnter { file ->
                val parentFolder = rootNode.findFolder(file.parentFile.absolutePath)
                parentFolder.children.add(FolderNode(file.absolutePath, mutableListOf()))

                true
            }
            .onFail { file, ioException -> ioException.printStackTrace() }
            .forEach {
                if (it.isFile) {
                    val parentFolder = rootNode.findFolder(it.parentFile.absolutePath)
                    parentFolder.children.add(FileNode(it.absolutePath, it.getHash()))
                }
            }

        return rootNode
    }

    private fun FolderNode.findFolder(path: String): FolderNode {
        val childFolders = this.children.mapNotNull { it as? FolderNode }

        childFolders.forEach {
            if (it.path == path) {
                return it
            }
        }

        childFolders.forEach {
            val childFolder = it.findFolder(path)
            if (childFolder.path == path) {
                return childFolder
            }
        }

        return this
    }

    private fun File.getHash(): String = this.inputStream().use { inputStream ->
        val contentHash = DigestUtils.md5Hex(inputStream)
        val pathHash = DigestUtils.md5Hex(this.absolutePath)
        DigestUtils.md5Hex(contentHash + pathHash)
    }
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
