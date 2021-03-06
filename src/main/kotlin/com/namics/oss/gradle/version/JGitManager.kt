/**
 * MIT License
 *
 * Copyright (c) 2019 Namics AG
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.namics.oss.gradle.version

import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode.TRACK
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.GitAPIException
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.transport.URIish
import org.eclipse.jgit.transport.sshd.SshdSessionFactory
import org.gradle.api.GradleException
import org.gradle.api.Project

public class JGitManager(val remoteUri: String? = null,
                         var remote: String? = null,
                         val initialBranch: String? = null,
                         val project: Project) : GitManager {

    private val repository = FileRepositoryBuilder().setWorkTree(project.projectDir).build()
    private val git: Git = Git(repository);
    private val sshSessionFactory: SshdSessionFactory = SshdSessionFactory()
    val skipPush = "true" == project.findProperty("skipPush")

    init {
        if (remote == null)
            remote = "origin"
        if (remoteUri != null) {
            if (remote == "origin")
                git.remoteRemove().setRemoteName(remote).call()
            git.remoteAdd()
                    .setName(remote)
                    .setUri(URIish(remoteUri))
                    .call()
            git.fetch().setRemote(remote)
                    .setTransportConfigCallback {
                        (it as SshTransport).sshSessionFactory = sshSessionFactory
                    }.call()
        }
        initialBranch?.let { checkout(it) }
    }

    override fun branch(): String {
        return repository.branch
    }

    override fun add(file: String) {
        git.add().addFilepattern(file).setUpdate(true).call()
    }

    override fun commit(message: String) {
        git.commit().setMessage("[Bot] $message").call()
    }

    override fun tag(version: SemVer) {
        git.tag().setMessage("[Bot] Release $version: create tag").call()
    }

    private fun checkoutRemote(branch: String) {
        git.checkout()
                .setCreateBranch(true)
                .setName(branch)
                .setUpstreamMode(TRACK)
                .setStartPoint("$remote/$branch")
                .call()
    }

    override fun checkout(branch: String) {
        try {
            git.checkout()
                    .setName(branch)
                    .call();
        } catch (e: GitAPIException) {
            checkoutRemote(branch);
        }
    }

    override fun merge(branch: String) {
        val mergeBase = repository.resolve(branch)
        val result = git.merge()
                .include(mergeBase)
                .setMessage("[Bot] merge $branch")
                .setCommit(true)
                .call()
        if (!result.mergeStatus.isSuccessful) {
            throw GradleException("Failed to merge branch $branch: $result")
        }
    }

    override fun push() {
        if (skipPush) return
        git.push()
                .setPushTags()
                .setPushAll()
                .setRemote(remote)
                .setTransportConfigCallback {
                    (it as SshTransport).sshSessionFactory = sshSessionFactory
                }.call()
    }

}



