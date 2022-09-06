/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.lsp.xml.providers.completion.common

import com.android.aapt.Resources.Attribute.FormatFlags
import com.android.aapt.Resources.Attribute.FormatFlags.BOOLEAN
import com.android.aapt.Resources.Attribute.FormatFlags.COLOR
import com.android.aapt.Resources.Attribute.FormatFlags.DIMENSION
import com.android.aapt.Resources.Attribute.FormatFlags.ENUM
import com.android.aapt.Resources.Attribute.FormatFlags.FLAGS
import com.android.aapt.Resources.Attribute.FormatFlags.INTEGER
import com.android.aapt.Resources.Attribute.FormatFlags.REFERENCE
import com.android.aapt.Resources.Attribute.FormatFlags.STRING
import com.android.aaptcompiler.AaptResourceType
import com.android.aaptcompiler.AaptResourceType.ATTR
import com.android.aaptcompiler.AaptResourceType.BOOL
import com.android.aaptcompiler.AaptResourceType.DIMEN
import com.android.aaptcompiler.AaptResourceType.UNKNOWN
import com.android.aaptcompiler.AttributeResource
import com.android.aaptcompiler.ConfigDescription
import com.android.aaptcompiler.ResourceGroup
import com.android.aaptcompiler.ResourcePathData
import com.itsaky.androidide.aapt.findEntries
import com.itsaky.androidide.lsp.api.ICompletionProvider
import com.itsaky.androidide.lsp.models.CompletionItem
import com.itsaky.androidide.lsp.models.CompletionParams
import com.itsaky.androidide.lsp.models.CompletionResult
import com.itsaky.androidide.lsp.models.CompletionResult.Companion.EMPTY
import com.itsaky.androidide.lsp.models.CompletionResult.Companion.MAX_ITEMS
import com.itsaky.androidide.lsp.models.MatchLevel.NO_MATCH
import com.itsaky.androidide.lsp.xml.providers.completion.IXmlCompletionProvider
import com.itsaky.androidide.lsp.xml.utils.XmlUtils.NodeType
import com.itsaky.androidide.lsp.xml.utils.XmlUtils.NodeType.ATTRIBUTE_VALUE
import org.eclipse.lemminx.dom.DOMDocument

/**
 * Provides completions for attribute value in layout XML files.
 *
 * @author Akash Yadav
 */
open class AttrValueCompletionProvider(provider: ICompletionProvider) :
  IXmlCompletionProvider(provider) {

  override fun canProvideCompletions(pathData: ResourcePathData, type: NodeType): Boolean {
    return super.canProvideCompletions(pathData, type) && type == ATTRIBUTE_VALUE
  }

  override fun doComplete(
    params: CompletionParams,
    pathData: ResourcePathData,
    document: DOMDocument,
    type: NodeType,
    prefix: String
  ): CompletionResult {
    val attrName =
      attrAtCursor.localName
        ?: run {
          log.warn("Cannot find attribute at index ${params.position.index}")
          return EMPTY
        }

    // TODO Currently we do not support completing values for attributes without a namespace
    //  For example, completions will be provided for: 'android:textColor="@@cursor@@"' but
    //  not for 'textColor="@@cursor"'

    val namespace =
      attrAtCursor.namespaceURI
        ?: run {
          log.warn("Unknown namespace for attribute", attrAtCursor)
          return EMPTY
        }

    val tables = findResourceTables(namespace)
    if (tables.isEmpty()) {
      return EMPTY
    }

    val pck = namespace.substringAfter(NAMESPACE_PREFIX)
    val list = mutableListOf<CompletionItem>()
    val groups = mutableSetOf<Pair<String, ResourceGroup>>()

    for (table in tables) {
      if (namespace == NAMESPACE_AUTO) {
        val grps =
          table.packages.filter { it.name.isNotBlank() }.map { it.name to it.findGroup(ATTR) }
        for (grp in grps) {
          grp.second?.also { groups.add(grp.first to it) }
        }
      } else {
        val grp = table.findPackage(pck)?.findGroup(ATTR) ?: continue
        groups.add((pck to grp))
      }
    }

    completeInternal(attrName, prefix, groups, list)

    return CompletionResult(list)
  }

  protected open fun completeInternal(
    attrName: String,
    prefix: String,
    groups: MutableSet<Pair<String, ResourceGroup>>,
    result: MutableList<CompletionItem>
  ) {
    for (pair in groups) {
      val pack = pair.first
      val group = pair.second
      val entry = group.findEntry(attrName)?.findValue(ConfigDescription())?.value ?: continue
      if (entry !is AttributeResource) {
        continue
      }

      addFromTable(entry, pack, prefix, result)
    }
  }

  private fun addFromTable(
    entry: AttributeResource,
    pck: String,
    prefix: String,
    list: MutableList<CompletionItem>
  ) {
    if (entry.typeMask == FormatFlags.REFERENCE_VALUE) {
      completeReferences(prefix, list)
    } else {
      // Check for specific attribute formats
      if (entry.hasType(STRING)) {
        addValues(type = AaptResourceType.STRING, prefix = prefix, result = list)
      }

      if (entry.hasType(INTEGER)) {
        addValues(type = AaptResourceType.INTEGER, prefix = prefix, result = list)
      }

      if (entry.hasType(COLOR)) {
        addValues(type = AaptResourceType.COLOR, prefix = prefix, result = list)
      }

      if (entry.hasType(BOOLEAN)) {
        addValues(type = BOOL, prefix = prefix, result = list)
      }

      if (entry.hasType(DIMENSION)) {
        addValues(type = DIMEN, prefix = prefix, result = list)
      }

      if (entry.hasType(INTEGER)) {
        addValues(type = AaptResourceType.INTEGER, prefix = prefix, result = list)
      }

      if (entry.hasType(ENUM) || entry.hasType(FLAGS)) {
        for (symbol in entry.symbols) {
          val matchLevel = matchLevel(symbol.symbol.name.entry!!, prefix)
          if (matchLevel == NO_MATCH && prefix.isNotEmpty()) {
            continue
          }

          list.add(
            createEnumOrFlagCompletionItem(pck = pck, name = symbol.symbol.name.entry!!, matchLevel)
          )
        }
      }

      if (entry.hasType(REFERENCE)) {
        completeReferences(prefix, list)
      }
    }
  }

  private fun completeReferences(prefix: String, list: MutableList<CompletionItem>) {
    for (value in AaptResourceType.values()) {
      if (value == UNKNOWN) {
        continue
      }

      addValues(value, prefix, list)
    }
  }

  private fun addValues(
    type: AaptResourceType,
    prefix: String,
    result: MutableList<CompletionItem>
  ) {
    if (result.size >= MAX_ITEMS + 1) {
      return
    }

    val entries =
      allNamespaces
        .flatMap { findResourceTables(it.second) }
        .flatMap { table ->
          table.packages.map { pck ->
            pck.name to pck.findGroup(type)?.findEntries { s -> matchLevel(s, prefix) != NO_MATCH }
          }
        }
        .toHashSet()
    entries.forEach { pair ->
      pair.second?.forEach {
        if (result.size >= MAX_ITEMS + 1) {
          return
        }

        result.add(
          createAttrValueCompletionItem(
            pair.first,
            type.tagName,
            it.name,
            matchLevel(it.name, prefix)
          )
        )
      }
    }
  }

  private fun AttributeResource.hasType(check: FormatFlags): Boolean {
    return hasType(check.number)
  }

  private fun AttributeResource.hasType(check: Int): Boolean {
    return this.typeMask and check != 0
  }
}