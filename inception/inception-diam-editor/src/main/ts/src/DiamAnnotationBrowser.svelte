<script lang="ts">
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

    import { onMount, onDestroy } from "svelte";
    import { get_current_component } from "svelte/internal";
    import {
        AnnotatedText,
        unpackCompactAnnotatedTextV2,
    } from "@inception-project/inception-js-api";
    import { factory } from "@inception-project/inception-diam";
    import {
        groupingMode,
        recommendationsFirst,
        sortByScore,
    } from "./AnnotationBrowserState";
    import AnnotationsByPositionList from "./AnnotationsByPositionList.svelte";
    import AnnotationsByLabelList from "./AnnotationsByLabelList.svelte";
    import AnnotationDetailPopOver from "@inception-project/inception-js-api/src/widget/AnnotationDetailPopOver.svelte"

    export let wsEndpointUrl: string;
    export let topicChannel: string;
    export let ajaxEndpointUrl: string;
    export let pinnedGroups: string[];
    export let userPreferencesKey: string;

    let connected = false;
    let element = null;
    let self = get_current_component();

    let defaultPreferences = {
        mode: "by-label",
        sortByScore: true,
        recommendationsFirst: false,
    };
    let preferences = Object.assign({}, defaultPreferences);
    let modes = {
        "by-position": "Group by position",
        "by-label": "Group by label",
    };
    let tooManyAnnotations = false;

    let data: AnnotatedText;

    let wsClient = factory().createWebsocketClient();
    wsClient.onConnect = () =>
        wsClient.subscribeToViewport(topicChannel, (d) => messageRecieved(d));

    let ajaxClient = factory().createAjaxClient(ajaxEndpointUrl);

    ajaxClient.loadPreferences(userPreferencesKey).then((p) => {
        preferences = Object.assign(preferences, defaultPreferences, p);
        console.log("Loaded preferences", preferences);
        groupingMode.set(preferences.mode || defaultPreferences.mode);
        sortByScore.set(
            preferences.sortByScore !== undefined
                ? preferences.sortByScore
                : defaultPreferences.sortByScore
        );
        recommendationsFirst.set(
            preferences.recommendationsFirst !== undefined
                ? preferences.recommendationsFirst
                : defaultPreferences.recommendationsFirst
        );

        groupingMode.subscribe((mode) => {
            preferences.mode = mode;
            ajaxClient.savePreferences(userPreferencesKey, preferences);
        });

        sortByScore.subscribe((mode) => {
            preferences.sortByScore = mode;
            ajaxClient.savePreferences(userPreferencesKey, preferences);
        });

        recommendationsFirst.subscribe((mode) => {
            preferences.recommendationsFirst = mode;
            ajaxClient.savePreferences(userPreferencesKey, preferences);
        });
    });

    export function messageRecieved(d) {
        if (!document.body.contains(element)) {
            console.debug(
                "Element is not part of the DOM anymore. Disconnecting and suiciding."
            );
            self.$destroy();
            return;
        }

        let preData = unpackCompactAnnotatedTextV2(d);
        if (preData.spans.size + preData.relations.size > 25000) {
            console.error(`Too many annotations: ${preData.spans.size} spans ${preData.relations.size} relations`)
            data = undefined
            tooManyAnnotations = true
        }
        else {
            console.info(`Loaded annotations: ${preData.spans.size} spans ${preData.relations.size} relations`)
            tooManyAnnotations = false
            data = preData;
        }
    }

    export function connect(): void {
        if (connected) return;
        wsClient.connect(wsEndpointUrl);
    }

    export function disconnect() {
        wsClient.unsubscribeFromViewport();
        wsClient.disconnect();
        connected = false;
    }

    onMount(async () => { 
        connect()
        new AnnotationDetailPopOver({
            target: element,
            props: {
                root: element,
                ajax: ajaxClient
            }
        })
    });

    onDestroy(async () => disconnect());
</script>

<div class="flex-content flex-v-container" bind:this={element}>
    <select bind:value={$groupingMode} class="form-select">
        {#each Object.keys(modes) as value}<option {value}
                >{modes[value]}</option
            >{/each}
    </select>
    {#if tooManyAnnotations}
        <div class="m-auto text-center text-muted">
            <div class="fs-1"><i class="far fa-dizzy"></i></div>
            <div>Too many annotations</div>
        </div>
    {:else if $groupingMode == "by-position"}
        <AnnotationsByPositionList {ajaxClient} {data} />
    {:else}
        <AnnotationsByLabelList {ajaxClient} {data} {pinnedGroups} />
    {/if}
</div>

<style>
     @import '../node_modules/@inception-project/inception-js-api/src/style/InceptionEditorIcons.scss';
</style>
