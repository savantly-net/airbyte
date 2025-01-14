
        
        
    

    

    merge into `dataline-integration-testing`.test_normalization.`exchange_rate` as DBT_INTERNAL_DEST
        using (
          select * from `dataline-integration-testing`.test_normalization.`exchange_rate__dbt_tmp`
        ) as DBT_INTERNAL_SOURCE
        on 
            DBT_INTERNAL_SOURCE._airbyte_ab_id = DBT_INTERNAL_DEST._airbyte_ab_id
        

    
    when matched then update set
        `id` = DBT_INTERNAL_SOURCE.`id`,`currency` = DBT_INTERNAL_SOURCE.`currency`,`date` = DBT_INTERNAL_SOURCE.`date`,`timestamp_col` = DBT_INTERNAL_SOURCE.`timestamp_col`,`HKD_special___characters` = DBT_INTERNAL_SOURCE.`HKD_special___characters`,`HKD_special___characters_1` = DBT_INTERNAL_SOURCE.`HKD_special___characters_1`,`NZD` = DBT_INTERNAL_SOURCE.`NZD`,`USD` = DBT_INTERNAL_SOURCE.`USD`,`_airbyte_ab_id` = DBT_INTERNAL_SOURCE.`_airbyte_ab_id`,`_airbyte_emitted_at` = DBT_INTERNAL_SOURCE.`_airbyte_emitted_at`,`_airbyte_normalized_at` = DBT_INTERNAL_SOURCE.`_airbyte_normalized_at`,`_airbyte_exchange_rate_hashid` = DBT_INTERNAL_SOURCE.`_airbyte_exchange_rate_hashid`
    

    when not matched then insert
        (`id`, `currency`, `date`, `timestamp_col`, `HKD_special___characters`, `HKD_special___characters_1`, `NZD`, `USD`, `_airbyte_ab_id`, `_airbyte_emitted_at`, `_airbyte_normalized_at`, `_airbyte_exchange_rate_hashid`)
    values
        (`id`, `currency`, `date`, `timestamp_col`, `HKD_special___characters`, `HKD_special___characters_1`, `NZD`, `USD`, `_airbyte_ab_id`, `_airbyte_emitted_at`, `_airbyte_normalized_at`, `_airbyte_exchange_rate_hashid`)


  