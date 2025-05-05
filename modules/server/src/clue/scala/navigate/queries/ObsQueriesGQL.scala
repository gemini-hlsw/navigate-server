// Copyright (c) 2016-2023 Association of Universities for Research in Astronomy, Inc. (AURA)
// For license information see LICENSE or https://opensource.org/licenses/BSD-3-Clause

package navigate.queries

import clue.GraphQLOperation
import clue.annotation.GraphQL
import lucuma.schemas.ObservationDB

object ObsQueriesGQL:

  @GraphQL
  trait AddSlewEventMutation extends GraphQLOperation[ObservationDB]:
    val document = """
      mutation($obsId: ObservationId!, $stg: SlewStage!)  {
        addSlewEvent(input: { observationId: $obsId, slewStage: $stg } ) {
          event {
            id
          }
        }
      }
      """
