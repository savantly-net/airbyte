
      delete
    from "integrationtests".test_normalization."renamed_dedup_cdc_excluded_scd"
    where (_airbyte_unique_key_scd) in (
        select (_airbyte_unique_key_scd)
        from "renamed_dedup_cdc_excluded_scd__dbt_tmp"
    );

    insert into "integrationtests".test_normalization."renamed_dedup_cdc_excluded_scd" ("_airbyte_unique_key", "_airbyte_unique_key_scd", "id", "_airbyte_start_at", "_airbyte_end_at", "_airbyte_active_row", "_airbyte_ab_id", "_airbyte_emitted_at", "_airbyte_normalized_at", "_airbyte_renamed_dedup_cdc_excluded_hashid", "name", "_ab_cdc_lsn", "_ab_cdc_updated_at", "_ab_cdc_deleted_at")
    (
       select "_airbyte_unique_key", "_airbyte_unique_key_scd", "id", "_airbyte_start_at", "_airbyte_end_at", "_airbyte_active_row", "_airbyte_ab_id", "_airbyte_emitted_at", "_airbyte_normalized_at", "_airbyte_renamed_dedup_cdc_excluded_hashid", "name", "_ab_cdc_lsn", "_ab_cdc_updated_at", "_ab_cdc_deleted_at"
       from "renamed_dedup_cdc_excluded_scd__dbt_tmp"
    );
  