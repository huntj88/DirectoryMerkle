package me.jameshunt.merkle

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.apache.commons.codec.digest.DigestUtils
import java.io.File

fun main() {

    val existingTree = File("src/main/resources/existing.json").treeFromJson()
    val directoryTree = Tree(File("src/main/resources/testDir"))
    println("isSame: ${directoryTree.isSame(existingTree)}")
    existingTree.run {
        this.folderNode.compareFolders(directoryTree.folderNode)
    }

    ObjectMapper()
        .apply { this.configure(SerializationFeature.INDENT_OUTPUT, true) }
        .writeValueAsString(directoryTree.folderNode).let { println(it) }
}

class Tree(internal val folderNode: FolderNode) {

    constructor(folder: File) : this(folder.buildTree())

    fun isSame(other: Tree): Boolean = folderNode.hash == other.folderNode.hash

    fun FolderNode.compareFolders(other: FolderNode) {
        when (this.hash == other.hash) {
            true -> println("no differences")
            false -> {
                val noMatchLocal = this.children.filter { localNode ->
                    other.children.firstOrNull { it.hash == localNode.hash } == null
                }

                val noMatchOther = other.children.filter { otherNode ->
                    this.children.firstOrNull { it.hash == otherNode.hash } == null
                }

                val noMatchLocalFolders = noMatchLocal.mapNotNull { it as? FolderNode }
                val noMatchLocalFiles = noMatchLocal.mapNotNull { it as? FileNode }

                val noMatchOtherFolders = noMatchOther.mapNotNull { it as? FolderNode }
                val noMatchOtherFiles = noMatchOther.mapNotNull { it as? FileNode }

                noMatchLocalFiles.forEach {
                    if (noMatchOtherFiles.map { it.path }.contains(it.path)) {
                        println("File changed: ${it.path}")
                    } else {
                        println("File removed: ${it.path}")
                    }
                }

                noMatchOtherFiles.forEach {
                    if (!noMatchLocalFiles.map { it.path }.contains(it.path)) {
                        println("File added: ${it.path}")
                    }
                }

                noMatchLocalFolders.forEach { localFolder ->
                    noMatchOtherFolders.firstOrNull { localFolder.path == it.path }?.let {
                        localFolder.compareFolders(it)
                    } ?: println("deleted folder: ${localFolder.path}")
                }

                noMatchOtherFolders.map { it.path }.forEach { otherPath ->
                    if (!noMatchLocalFolders.map { it.path }.contains(otherPath)) {
                        println("added folder: $otherPath")
                    }
                }
            }
        }
    }
}

private fun File.treeFromJson(): Tree {
    val json = ObjectMapper().readValue<JsonObject>(this.readText(), object : TypeReference<JsonObject>() {})
    return Tree(json.treeFromJson() as FolderNode)
}

typealias JsonObject = Map<String, Any?>

private fun JsonObject.treeFromJson(): Node {
    return when (this["children"] != null) {
        true -> {
            val childrenNodes = (this["children"] as List<JsonObject>).map { it.treeFromJson() }
            FolderNode(
                path = this["path"] as String,
                children = childrenNodes.toMutableList(),
                _hash = this["hash"] as String
            )
        }
        false -> {
            FileNode(
                path = this["path"] as String,
                hash = this["hash"] as String
            )
        }
    }
}

private fun File.buildTree(): FolderNode {
    val rootNode = FolderNode("", mutableListOf())

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

    return rootNode.children.first() as FolderNode
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
    val children: MutableList<Node>,
    private var _hash: String? = null
) : Node {
    override val hash: String
        get() = _hash ?: children
            .joinToString("") { it.hash }
            .let { DigestUtils.md5Hex(path + it) }
            .also { _hash = it }
}
