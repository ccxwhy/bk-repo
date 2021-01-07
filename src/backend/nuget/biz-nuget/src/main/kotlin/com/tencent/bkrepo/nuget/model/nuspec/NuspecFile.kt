package com.tencent.bkrepo.nuget.model.nuspec

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import java.io.Serializable

@JacksonXmlRootElement(localName = "file")
data class NuspecFile (
    @JacksonXmlProperty(isAttribute = true)
    val src: String,
    @JacksonXmlProperty(isAttribute = true)
    val target: String?,
    @JacksonXmlProperty(isAttribute = true)
    val exclude: String?
) : Serializable