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

import {EnvironmentsJSON} from "models/environments/types";

export class TestData {
  static environmentList(...objects: any[]) {
    return {
      _links: {
        self: {
          href: "https://ci.example.com/go/api/admin/environments"
        },
        doc: {
          href: "https://api.gocd.org/#environment-config"
        },
        find: {
          href: "https://ci.example.com/go/api/admin/environments/:environment_name"
        }
      },
      _embedded: {
        environments: objects
      }
    } as EnvironmentsJSON;
  }

  static newEnvironment(name: string = "env", agents: string[] = [], pipelines: string[] = []) {
    const agentsJSON   = agents.map((a) => {
      return {uuid: a};
    });
    const pipelineJSON = pipelines.map((p) => {
      return {name: p};
    });
    return {
      name,
      agents: agentsJSON,
      pipelines: pipelineJSON,
      environment_variables: []
    };
  }
}
