/*
 * Licensed to the Technische Universität Darmstadt under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The Technische Universität Darmstadt 
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.
 *  
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.preferences;

public class ClientSidePreferencesKey<T>
    extends Key<T>
{
    public static final String KEY_PREFIX_CLIENT_SIDE_ANNOTATION = "client-side/";

    private final String clientSideKey;

    public ClientSidePreferencesKey(Class<T> aTraitClass, String aClientSideKey)
    {
        super(aTraitClass, KEY_PREFIX_CLIENT_SIDE_ANNOTATION + aClientSideKey);
        clientSideKey = aClientSideKey;
    }

    public String getClientSideKey()
    {
        return clientSideKey;
    }
}
