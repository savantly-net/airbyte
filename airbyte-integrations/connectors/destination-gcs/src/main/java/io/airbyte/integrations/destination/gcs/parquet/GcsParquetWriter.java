/*
 * Copyright (c) 2021 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.destination.gcs.parquet;

import com.amazonaws.services.s3.AmazonS3;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.airbyte.integrations.base.JavaBaseConstants;
import io.airbyte.integrations.destination.gcs.GcsDestinationConfig;
import io.airbyte.integrations.destination.gcs.credential.GcsHmacKeyCredentialConfig;
import io.airbyte.integrations.destination.gcs.writer.BaseGcsWriter;
import io.airbyte.integrations.destination.s3.S3Format;
import io.airbyte.integrations.destination.s3.avro.JsonFieldNameUpdater;
import io.airbyte.integrations.destination.s3.parquet.S3ParquetFormatConfig;
import io.airbyte.integrations.destination.s3.writer.S3Writer;
import io.airbyte.protocol.models.AirbyteRecordMessage;
import io.airbyte.protocol.models.ConfiguredAirbyteStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Timestamp;
import java.util.UUID;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericData.Record;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.util.HadoopOutputFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.allegro.schema.json2avro.converter.JsonAvroConverter;

public class GcsParquetWriter extends BaseGcsWriter implements S3Writer {

  private static final Logger LOGGER = LoggerFactory.getLogger(GcsParquetWriter.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final ObjectWriter WRITER = MAPPER.writer();

  private final Schema schema;
  private final JsonFieldNameUpdater nameUpdater;
  private final ParquetWriter<Record> parquetWriter;
  private final JsonAvroConverter converter = new JsonAvroConverter();

  public GcsParquetWriter(final GcsDestinationConfig config,
                          final AmazonS3 s3Client,
                          final ConfiguredAirbyteStream configuredStream,
                          final Timestamp uploadTimestamp,
                          final Schema schema,
                          final JsonFieldNameUpdater nameUpdater)
      throws URISyntaxException, IOException {
    super(config, s3Client, configuredStream);
    this.schema = schema;
    this.nameUpdater = nameUpdater;

    final String outputFilename = BaseGcsWriter.getOutputFilename(uploadTimestamp, S3Format.PARQUET);
    final String objectKey = String.join("/", outputPrefix, outputFilename);
    LOGGER.info("Storage path for stream '{}': {}/{}", stream.getName(), config.getBucketName(), objectKey);

    final URI uri = new URI(String.format("s3a://%s/%s/%s", config.getBucketName(), outputPrefix, outputFilename));
    final Path path = new Path(uri);

    LOGGER.info("Full GCS path for stream '{}': {}", stream.getName(), path);

    final S3ParquetFormatConfig formatConfig = (S3ParquetFormatConfig) config.getFormatConfig();
    final Configuration hadoopConfig = getHadoopConfig(config);
    this.parquetWriter = AvroParquetWriter.<GenericData.Record>builder(HadoopOutputFile.fromPath(path, hadoopConfig))
        .withSchema(schema)
        .withCompressionCodec(formatConfig.getCompressionCodec())
        .withRowGroupSize(formatConfig.getBlockSize())
        .withMaxPaddingSize(formatConfig.getMaxPaddingSize())
        .withPageSize(formatConfig.getPageSize())
        .withDictionaryPageSize(formatConfig.getDictionaryPageSize())
        .withDictionaryEncoding(formatConfig.isDictionaryEncoding())
        .build();
  }

  public static Configuration getHadoopConfig(final GcsDestinationConfig config) {
    final GcsHmacKeyCredentialConfig hmacKeyCredential = (GcsHmacKeyCredentialConfig) config.getCredentialConfig();
    final Configuration hadoopConfig = new Configuration();

    // the default org.apache.hadoop.fs.s3a.S3AFileSystem does not work for GCS
    hadoopConfig.set("fs.s3a.impl", "io.airbyte.integrations.destination.gcs.util.GcsS3FileSystem");

    // https://stackoverflow.com/questions/64141204/process-data-in-google-storage-on-an-aws-emr-cluster-in-spark
    hadoopConfig.set("fs.s3a.access.key", hmacKeyCredential.getHmacKeyAccessId());
    hadoopConfig.set("fs.s3a.secret.key", hmacKeyCredential.getHmacKeySecret());
    hadoopConfig.setBoolean("fs.s3a.path.style.access", true);
    hadoopConfig.set("fs.s3a.endpoint", "storage.googleapis.com");
    hadoopConfig.setInt("fs.s3a.list.version", 1);

    return hadoopConfig;
  }

  @Override
  public void write(final UUID id, final AirbyteRecordMessage recordMessage) throws IOException {
    JsonNode inputData = recordMessage.getData();
    inputData = nameUpdater.getJsonWithStandardizedFieldNames(inputData);

    final ObjectNode jsonRecord = MAPPER.createObjectNode();
    jsonRecord.put(JavaBaseConstants.COLUMN_NAME_AB_ID, UUID.randomUUID().toString());
    jsonRecord.put(JavaBaseConstants.COLUMN_NAME_EMITTED_AT, recordMessage.getEmittedAt());
    jsonRecord.setAll((ObjectNode) inputData);

    final GenericData.Record avroRecord = converter.convertToGenericDataRecord(WRITER.writeValueAsBytes(jsonRecord), schema);
    parquetWriter.write(avroRecord);
  }

  @Override
  public void close(final boolean hasFailed) throws IOException {
    if (hasFailed) {
      LOGGER.warn("Failure detected. Aborting upload of stream '{}'...", stream.getName());
      parquetWriter.close();
      LOGGER.warn("Upload of stream '{}' aborted.", stream.getName());
    } else {
      LOGGER.info("Uploading remaining data for stream '{}'.", stream.getName());
      parquetWriter.close();
      LOGGER.info("Upload completed for stream '{}'.", stream.getName());
    }
  }

}
