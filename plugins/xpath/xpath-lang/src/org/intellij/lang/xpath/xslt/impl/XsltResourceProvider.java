/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.intellij.lang.xpath.xslt.impl;

import com.intellij.javaee.ResourceRegistrar;
import com.intellij.javaee.StandardResourceProvider;
import org.intellij.lang.xpath.xslt.XsltSupport;

/**
 * @author Dmitry Avdeev
 */
public class XsltResourceProvider implements StandardResourceProvider {

  private static final String XSLT_SCHEMA_LOCATION = "resources/xslt-schema.xsd";

  public void registerResources(ResourceRegistrar registrar) {
     registrar.addStdResource(XsltSupport.XSLT_NS, XSLT_SCHEMA_LOCATION, getClass());
  }
}
