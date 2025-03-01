{{ config(
    cluster_by = ["_AIRBYTE_EMITTED_AT"],
    unique_key = env_var('AIRBYTE_DEFAULT_UNIQUE_KEY', '_AIRBYTE_AB_ID'),
    schema = "TEST_NORMALIZATION",
    tags = [ "nested" ]
) }}
-- Final base SQL model
select
    _AIRBYTE_CONFLICT_STREAM_NAME_HASHID,
    CONFLICT_STREAM_NAME,
    _AIRBYTE_AB_ID,
    _AIRBYTE_EMITTED_AT,
    {{ current_timestamp() }} as _AIRBYTE_NORMALIZED_AT,
    _AIRBYTE_CONFLICT_STREAM_NAME_2_HASHID
from {{ ref('CONFLICT_STREAM_NAME_CONFLICT_STREAM_NAME_AB3') }}
-- CONFLICT_STREAM_NAME at conflict_stream_name/conflict_stream_name from {{ ref('CONFLICT_STREAM_NAME') }}
where 1 = 1

