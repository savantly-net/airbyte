

  create or replace view `dataline-integration-testing`._airbyte_test_normalization.`conflict_stream_array_ab2`
  OPTIONS()
  as 
-- SQL model to cast each column to its adequate SQL type converted from the JSON schema type
select
    cast(id as 
    string
) as id,
    conflict_stream_array,
    _airbyte_ab_id,
    _airbyte_emitted_at,
    CURRENT_TIMESTAMP() as _airbyte_normalized_at
from `dataline-integration-testing`._airbyte_test_normalization.`conflict_stream_array_ab1`
-- conflict_stream_array
where 1 = 1;

