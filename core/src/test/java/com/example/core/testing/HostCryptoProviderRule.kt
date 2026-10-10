package com.example.core.testing

import java.security.Provider
import java.security.Security
import org.junit.rules.ExternalResource

/** Use host-JVM AES for local crypto tests; restore Robolectric's providers after each test. */
class HostCryptoProviderRule : ExternalResource() {
    private var removed = emptyList<Pair<Provider, Int>>()

    override fun before() {
        removed =
            Security.getProviders().mapIndexedNotNull { index, provider ->
                if (provider.name == "Conscrypt" || provider.name == "BC") provider to index + 1
                else null
            }
        removed.forEach { Security.removeProvider(it.first.name) }
    }

    override fun after() {
        removed.forEach { (provider, index) -> Security.insertProviderAt(provider, index) }
    }
}
