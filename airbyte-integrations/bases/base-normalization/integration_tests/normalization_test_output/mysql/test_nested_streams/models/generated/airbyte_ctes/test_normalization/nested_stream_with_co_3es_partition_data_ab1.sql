{{ config(
    unique_key = env_var('AIRBYTE_DEFAULT_UNIQUE_KEY', '_airbyte_ab_id'),
    schema = "_airbyte_test_normalization",
    tags = [ "nested-intermediate" ]
) }}
-- SQL model to parse JSON blob stored in a single column and extract into separated field columns as described by the JSON Schema
{{ unnest_cte('nested_stream_with_co___long_names_partition', 'partition', adapter.quote('DATA')) }}
select
    _airbyte_partition_hashid,
    {{ json_extract_scalar(unnested_column_value(adapter.quote('DATA')), ['currency'], ['currency']) }} as currency,
    _airbyte_ab_id,
    _airbyte_emitted_at,
    {{ current_timestamp() }} as _airbyte_normalized_at
from {{ ref('nested_stream_with_co___long_names_partition') }} as table_alias
-- DATA at nested_stream_with_complex_columns_resulting_into_long_names/partition/DATA
{{ cross_join_unnest('partition', adapter.quote('DATA')) }}
where 1 = 1
and {{ adapter.quote('DATA') }} is not null

