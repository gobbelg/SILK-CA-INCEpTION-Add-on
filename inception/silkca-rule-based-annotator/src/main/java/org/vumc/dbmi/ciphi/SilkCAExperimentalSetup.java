/*
 * Licensed to the Vanderbilt University Medical Center under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The Vanderbilt University Medical Center
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
package org.vumc.dbmi.ciphi;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SilkCAExperimentalSetup
{
 // @formatter:off
    private static final Set<String> ANNOTATOR_01_PREANNOTATION = new HashSet<>(List.of(
            "GCS_MIMICIII_240109_002_004_001.txt",
            "GCS_MIMICIII_240109_002_004_003.txt",
            "GCS_MIMICIII_240109_002_004_005.txt",
            "GCS_MIMICIII_240109_002_004_007.txt",
            "GCS_MIMICIII_240109_002_004_009.txt",
            "GCS_MIMICIII_240109_002_004_011.txt",
            "GCS_MIMICIII_240109_002_004_013.txt",
            "GCS_MIMICIII_240109_002_004_015.txt",
            "GCS_MIMICIII_240109_002_004_017.txt",
            "GCS_MIMICIII_240109_002_004_019.txt",
            "GCS_MIMICIII_240109_002_005_001.txt",
            "GCS_MIMICIII_240109_002_005_003.txt",
            "GCS_MIMICIII_240109_002_005_005.txt",
            "GCS_MIMICIII_240109_002_005_007.txt",
            "GCS_MIMICIII_240109_002_005_009.txt",
            "GCS_MIMICIII_240109_002_005_011.txt",
            "GCS_MIMICIII_240109_002_005_013.txt",
            "GCS_MIMICIII_240109_002_005_015.txt",
            "GCS_MIMICIII_240109_002_005_017.txt",
            "GCS_MIMICIII_240109_002_005_019.txt"
            )
    );
 // @formatter:on
    
// @formatter:off
    private static final Set<String> ANNOTATOR_02_PREANNOTATION = new HashSet<>(List.of(
            "GCS_MIMICIII_240109_002_004_000.txt",
            "GCS_MIMICIII_240109_002_004_002.txt",
            "GCS_MIMICIII_240109_002_004_004.txt",
            "GCS_MIMICIII_240109_002_004_006.txt",
            "GCS_MIMICIII_240109_002_004_008.txt",
            "GCS_MIMICIII_240109_002_004_010.txt",
            "GCS_MIMICIII_240109_002_004_012.txt",
            "GCS_MIMICIII_240109_002_004_014.txt",
            "GCS_MIMICIII_240109_002_004_016.txt",
            "GCS_MIMICIII_240109_002_004_018.txt",
            "GCS_MIMICIII_240109_002_005_000.txt",
            "GCS_MIMICIII_240109_002_005_002.txt",
            "GCS_MIMICIII_240109_002_005_004.txt",
            "GCS_MIMICIII_240109_002_005_006.txt",
            "GCS_MIMICIII_240109_002_005_008.txt",
            "GCS_MIMICIII_240109_002_005_010.txt",
            "GCS_MIMICIII_240109_002_005_012.txt",
            "GCS_MIMICIII_240109_002_005_014.txt",
            "GCS_MIMICIII_240109_002_005_016.txt",
            "GCS_MIMICIII_240109_002_005_018.txt"
            )
    );
 // @formatter:on

    public static HashMap<String, Set<String>> preAnnotationAssignments;
    
    static {
        preAnnotationAssignments.put("jill", ANNOTATOR_01_PREANNOTATION);
        preAnnotationAssignments.put("tina", ANNOTATOR_02_PREANNOTATION);
    }
    
    public SilkCAExperimentalSetup() {
        
    }
}
