package me.jameshunt.merkle

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.apache.commons.codec.digest.DigestUtils
import java.io.File

fun main() {

    val existing = File("src/main/resources/existing.json").treeFromJson()


    val rootFile = File("src/main/resources/testDir")
    val tree = Tree(rootFile)
    println(tree.isSame(existing))

    ObjectMapper()
        .apply { this.configure(SerializationFeature.INDENT_OUTPUT, true) }
        .writeValueAsString(tree.folderNode).let { println(it) }
}

class Tree(internal val folderNode: FolderNode) {

    constructor(folder: File) : this(folder.buildTree())

    fun isSame(other: Tree): Boolean = folderNode.hash == other.folderNode.hash
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
