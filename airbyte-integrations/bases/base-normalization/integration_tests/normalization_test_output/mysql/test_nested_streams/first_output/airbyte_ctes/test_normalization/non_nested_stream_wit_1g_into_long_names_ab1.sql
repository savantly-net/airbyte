
  create view _airbyte_test_normalization.`non_nested_stream_wit_1g_into_long_names_ab1__dbt_tmp` as (
    
-- SQL model to parse JSON blob stored in a single column and extract into separated field columns as described by the JSON Schema
select
    json_value(_airbyte_data, 
    '$."id"') as id,
    json_value(_airbyte_data, 
    '$."date"') as `date`,
    _airbyte_ab_id,
    _airbyte_emitted_at,
    
    CURRENT_TIMESTAMP
 as _airbyte_normalized_at
from test_normalization._airbyte_raw_non_nest__lting_into_long_names as table_alias
-- non_nested_stream_wit__lting_into_long_names
where 1 = 1
  );
