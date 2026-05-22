package org.wip.plugintoolkit.api

import java.text.Normalizer

actual fun normalizeNFKC(str: String): String {
    return Normalizer.normalize(str, Normalizer.Form.NFKC)
}
