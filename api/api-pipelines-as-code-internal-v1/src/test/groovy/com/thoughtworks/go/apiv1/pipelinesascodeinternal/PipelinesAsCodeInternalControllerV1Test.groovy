/*
 * Copyright 2019 ThoughtWorks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.thoughtworks.go.apiv1.pipelinesascodeinternal

import com.thoughtworks.go.api.SecurityTestTrait
import com.thoughtworks.go.api.spring.ApiAuthenticationHelper
import com.thoughtworks.go.config.ConfigRepoPlugin
import com.thoughtworks.go.config.CruiseConfig
import com.thoughtworks.go.config.GoConfigPluginService
import com.thoughtworks.go.config.PipelineConfig
import com.thoughtworks.go.config.materials.PasswordDeserializer
import com.thoughtworks.go.config.update.CreatePipelineConfigCommand
import com.thoughtworks.go.server.service.PipelineConfigService
import com.thoughtworks.go.spark.AdminUserSecurity
import com.thoughtworks.go.spark.ControllerTrait
import com.thoughtworks.go.spark.SecurityServiceTrait
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer

import static com.thoughtworks.go.plugin.access.configrepo.ExportedConfig.from
import static org.mockito.ArgumentMatchers.*
import static org.mockito.Mockito.*
import static org.mockito.MockitoAnnotations.initMocks

class PipelinesAsCodeInternalControllerV1Test implements SecurityServiceTrait, ControllerTrait<PipelinesAsCodeInternalControllerV1> {

  private static final String PLUGIN_ID = "test.config.plugin"
  private static final String GROUP_NAME = "test-group"
  private static final String PIPELINE_NAME = "test-pipeline"

  @Mock
  GoConfigPluginService pluginService

  @Mock
  PasswordDeserializer passwordDeserializer

  @Mock
  ConfigRepoPlugin configRepoPlugin

  @Mock
  PipelineConfigService pipelineService

  @BeforeEach
  void setUp() {
    initMocks(this)
  }

  @Override
  PipelinesAsCodeInternalControllerV1 createControllerInstance() {
    new PipelinesAsCodeInternalControllerV1(new ApiAuthenticationHelper(securityService, goConfigService), passwordDeserializer, goConfigService, pluginService, pipelineService)
  }

  @Nested
  class Preview {

    @Nested
    class Security implements SecurityTestTrait, AdminUserSecurity {

      @Override
      String getControllerMethodUnderTest() {
        return "preview"
      }

      @Override
      void makeHttpCall() {
        postWithApiHeader(controller.controllerPath([group: GROUP_NAME], "preview", PLUGIN_ID), [:], [
          name: PIPELINE_NAME
        ])
      }
    }

    @Nested
    class AsAdmin {

      private static final String ETAG = 'big_etag_for_export'

      @BeforeEach
      void setUp() {
        enableSecurity()
        loginAsAdmin()
      }

      @Test
      void 'should be able to export pipeline config if user is admin and etag is stale'() {
        when(configRepoPlugin.etagForExport(any(PipelineConfig), eq(GROUP_NAME))).thenReturn(ETAG)
        when(configRepoPlugin.pipelineExport(any(PipelineConfig), eq(GROUP_NAME))).thenReturn(from("message from plugin", [
          "Content-Type"     : "text/plain",
          "X-Export-Filename": "foo.txt"
        ]))

        when(pluginService.isConfigRepoPlugin(PLUGIN_ID)).thenReturn(true)
        when(pluginService.supportsPipelineExport(PLUGIN_ID)).thenReturn(true)
        when(pluginService.partialConfigProviderFor(PLUGIN_ID)).thenReturn(configRepoPlugin)

        postWithApiHeader(controller.controllerPath([group: GROUP_NAME], "preview", PLUGIN_ID), [:], [
          name: PIPELINE_NAME
        ])

        assertThatResponse()
          .isOk()
          .hasHeader("Etag", "\"$ETAG\"")
          .hasBody("message from plugin")
      }

      @Test
      void 'returns a 422 when plugin is not a configrepo plugin'() {
        when(pluginService.isConfigRepoPlugin(PLUGIN_ID)).thenReturn(false)

        postWithApiHeader(controller.controllerPath([group: GROUP_NAME], "preview", PLUGIN_ID), [:], [
          name: PIPELINE_NAME
        ])

        assertThatResponse()
          .isUnprocessableEntity()
          .hasJsonMessage("Plugin `$PLUGIN_ID` is not a Pipelines-as-Code plugin.")
      }

      @Test
      void 'returns a 422 when plugin does not support export'() {
        when(pluginService.isConfigRepoPlugin(PLUGIN_ID)).thenReturn(true)
        when(pluginService.supportsPipelineExport(PLUGIN_ID)).thenReturn(false)

        postWithApiHeader(controller.controllerPath([group: GROUP_NAME], "preview", PLUGIN_ID), [:], [
          name: PIPELINE_NAME
        ])

        assertThatResponse()
          .isUnprocessableEntity()
          .hasJsonMessage("Plugin `$PLUGIN_ID` does not support pipeline config export.")
      }

      @Test
      void "should return 304 for export pipeline config if etag matches"() {
        when(configRepoPlugin.etagForExport(any(PipelineConfig), eq(GROUP_NAME))).thenReturn(ETAG)
        when(pluginService.isConfigRepoPlugin(PLUGIN_ID)).thenReturn(true)
        when(pluginService.supportsPipelineExport(PLUGIN_ID)).thenReturn(true)
        when(pluginService.partialConfigProviderFor(PLUGIN_ID)).thenReturn(configRepoPlugin)

        postWithApiHeader(controller.controllerPath([group: GROUP_NAME], "preview", PLUGIN_ID), ["if-none-match": "\"$ETAG\""], [
          name: PIPELINE_NAME
        ])

        assertThatResponse()
          .hasBody("")
          .isNotModified()
          .hasContentType(controller.mimeType)
      }

      @Test
      void "validates ad-hoc pipeline config when ?validate=true"() {
        CreatePipelineConfigCommand cmd = mock(CreatePipelineConfigCommand)
        CruiseConfig config = mock(CruiseConfig)
        PipelineConfig pipeline = null;

        when(pluginService.isConfigRepoPlugin(PLUGIN_ID)).thenReturn(true)
        when(pluginService.supportsPipelineExport(PLUGIN_ID)).thenReturn(true)
        when(pluginService.partialConfigProviderFor(PLUGIN_ID)).thenReturn(configRepoPlugin)
        when(pipelineService.createPipelineConfigCommand(eq(currentUsername()), any(PipelineConfig), isNull(), eq(GROUP_NAME))).thenAnswer(new Answer<Object>() {
          @Override
          Object answer(InvocationOnMock invocation) throws Throwable {
            pipeline = invocation.getArgument(1, PipelineConfig)
            return cmd
          }
        })

        when(goConfigService.preprocessedCruiseConfigForPipelineUpdate(cmd)).thenReturn(config)
        when(cmd.isValid(config)).thenAnswer(new Answer<Object>() {
          @Override
          Object answer(InvocationOnMock invocation) throws Throwable {
            if (null == pipeline) {
              throw new IllegalStateException("Expected pipeline config to be set in this test")
            }

            pipeline.addError("name", "That's an uncreative name")
            return false
          }
        })

        postWithApiHeader(controller.controllerPath([group: GROUP_NAME, validate: "true"], "preview", PLUGIN_ID), [:], [
          name: PIPELINE_NAME
        ])

        verify(goConfigService, times(1)).preprocessedCruiseConfigForPipelineUpdate(cmd)
        verify(cmd, times(1)).isValid(config)

        String errorMessage = "Please fix the validation errors for pipeline $PIPELINE_NAME."

        assertThatResponse().
          isUnprocessableEntity().
          hasContentType(controller.mimeType).
          hasJsonBody([
            message: errorMessage,
            data   : [
              errors               : [
                name: ["That's an uncreative name"]
              ],
              label_template       : '${COUNT}',
              lock_behavior        : "none",
              name                 : PIPELINE_NAME,
              template             : null,
              origin               : [
                type: "gocd"
              ],
              parameters           : [],
              environment_variables: [],
              materials            : [],
              stages               : null,
              tracking_tool        : null,
              timer                : null
            ]
          ])
      }
    }

  }
}
