{{ config(
    sort = "_airbyte_emitted_at",
    unique_key = env_var('AIRBYTE_DEFAULT_UNIQUE_KEY', '_airbyte_ab_id'),
    schema = "_airbyte_test_normalization",
    tags = [ "top-level-intermediate" ]
) }}
-- SQL model to cast each column to its adequate SQL type converted from the JSON schema type
select
    cast(id as {{ dbt_utils.type_bigint() }}) as id,
    _airbyte_ab_id,
    _airbyte_emitted_at,
    {{ current_timestamp() }} as _airbyte_normalized_at
from {{ ref('renamed_dedup_cdc_excluded_ab1') }}
-- renamed_dedup_cdc_excluded
where 1 = 1
{{ incremental_clause('_airbyte_emitted_at') }}

