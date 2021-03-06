// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.indexing.zipFs.UncompressedZipFileSystem
import com.intellij.util.io.readText
import com.intellij.util.io.toByteArray
import com.intellij.util.io.zip.JBZipFile
import junit.framework.TestCase
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption

class UncompressedZipTest : TestCase() {

  @Throws(Exception::class)
  override fun setUp() {
  }

  @Throws(Exception::class)
  override fun tearDown() {
  }

  fun testFsStructure() {
    val dir = FileUtil.createTempDirectory("zip0-fs-structure-dir", null)

    val file = File(dir, "a.zip")
    val str = "Hello"
    val helloBytes = str.toByteArray(Charsets.UTF_8)
    JBZipFile(file).use {
      it.getOrCreateEntry("b.txt").data = helloBytes
      it.getOrCreateEntry("a/b.txt").data = helloBytes
      it.getOrCreateEntry("a/c.txt").data = helloBytes
    }

    val fs = UncompressedZipFileSystem.create(file.toPath())
    val file1 = fs.getPath("b.txt")
    val file2 = fs.getPath("a", "b.txt")
    val file3 = fs.getPath("a", "c.txt")

    assertTrue(Files.exists(file1))
    assertTrue(Files.exists(file2))
    assertTrue(Files.exists(file3))

    assertTrue(helloBytes.contentEquals(Files.readAllBytes(file1)))
    assertTrue(helloBytes.contentEquals(Files.readAllBytes(file2)))
    assertTrue(helloBytes.contentEquals(Files.readAllBytes(file3)))

    val parent2 = file2.parent
    val parent3 = file3.parent
    assertTrue(Files.isSameFile(parent2, parent3))

    val pp2 = parent2.parent
    val pp3 = parent3.parent
    val pp1 = file1.parent
    assertTrue(Files.isSameFile(pp1, pp2))
    assertTrue(Files.isSameFile(pp1, pp3))
    assertTrue(Files.isSameFile(pp2, pp3))

    fs.close()
  }

  fun testFsStructureSync() {
    val dir = FileUtil.createTempDirectory("zip0-fs-structure-dir", null)

    val file = File(dir, "a.zip")
    val helloBytes = "Hello".toByteArray(Charsets.UTF_8)
    JBZipFile(file).use {
      it.getOrCreateEntry("b.txt").data = helloBytes
      it.getOrCreateEntry("a/b.txt").data = helloBytes
      it.getOrCreateEntry("a/c.txt").data = helloBytes
    }

    val fs = UncompressedZipFileSystem.create(file.toPath())

    assertTrue(Files.exists(fs.getPath("b.txt")))
    assertTrue(Files.exists(fs.getPath("a", "b.txt")))
    assertTrue(Files.exists(fs.getPath("a", "c.txt")))
    assertFalse(Files.exists(fs.getPath("a", "d.txt")))

    JBZipFile(file).use {
      it.getOrCreateEntry("a/d.txt").data = helloBytes
    }

    fs.sync()

    assertTrue(Files.exists(fs.getPath("b.txt")))
    assertTrue(Files.exists(fs.getPath("a", "b.txt")))
    assertTrue(Files.exists(fs.getPath("a", "c.txt")))
    assertTrue(Files.exists(fs.getPath("a", "d.txt")))

    fs.close()
  }

  fun testChannelAfterSync() {
    val dir = FileUtil.createTempDirectory("zip0-fs-structure-dir", null)

    val file = File(dir, "archive.zip")
    val helloBytes = "Hello".toByteArray(Charsets.UTF_8)
    JBZipFile(file).use {
      it.getOrCreateEntry("a.txt").data = helloBytes
    }

    val fs = UncompressedZipFileSystem.create(file.toPath())

    val aPath = fs.getPath("a.txt")
    val aFile = FileChannel.open(aPath, StandardOpenOption.READ)
    assertTrue(Files.exists(aPath))

    JBZipFile(file).use {
      it.getOrCreateEntry("b.txt").data = helloBytes
    }

    fs.sync()

    val bPath = fs.getPath("b.txt")
    val bFile = FileChannel.open(bPath, StandardOpenOption.READ)
    assertTrue(Files.exists(bPath))

    val aByteBuffer = ByteBuffer.allocate(helloBytes.size)
    aFile.read(aByteBuffer)
    val bByteBuffer = ByteBuffer.allocate(helloBytes.size)
    bFile.read(bByteBuffer)

    assertTrue(helloBytes.contentEquals(aByteBuffer.toByteArray()))
    assertTrue(helloBytes.contentEquals(bByteBuffer.toByteArray()))

    fs.close()
  }

  fun testLongFileRead() {
    val dir = FileUtil.createTempDirectory("zip0-fs-structure-dir", null)

    val file = File(dir, "archive.zip")
    val text = "Hello".repeat(10_000)
    val helloBytes = text.toByteArray(Charsets.UTF_8)
    JBZipFile(file).use {
      it.getOrCreateEntry("a.txt").data = helloBytes
    }

    val fs = UncompressedZipFileSystem.create(file.toPath())

    val readText = fs.getPath("a.txt").readText()
    assertEquals(text, readText)

    fs.close()
  }
}
