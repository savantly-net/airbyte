{{ config(
    unique_key = env_var('AIRBYTE_DEFAULT_UNIQUE_KEY', '_airbyte_ab_id'),
    schema = "_airbyte_test_normalization",
    tags = [ "nested-intermediate" ]
) }}
-- SQL model to build a hash column based on the values of this record
select
    {{ dbt_utils.surrogate_key([
        '_airbyte_conflict_stream_name_hashid',
        'conflict_stream_name',
    ]) }} as _airbyte_conflict_stream_name_2_hashid,
    tmp.*
from {{ ref('conflict_stream_name__2flict_stream_name_ab2') }} tmp
-- conflict_stream_name at conflict_stream_name/conflict_stream_name
where 1 = 1

