package org.rent.app.domain;


import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import javax.persistence.Id;
import java.time.LocalDateTime;

@Data
@Builder
@Document(indexName = "product", createIndex = true)
public class ProductELK implements Persistable<Long> {
    @Id
    @Field(type = FieldType.Long)
    private Long id;
    @Field(type = FieldType.Text)
    private String name;
    @Field(type = FieldType.Text)
    private String description;
    @Field(type = FieldType.Text)
    private String brand;
    @Field(type = FieldType.Long)
    private Long category;
    @Field(type = FieldType.Long)
    private Long owner;
    @Field(type = FieldType.Long)
    private Long price;
    @CreatedDate
    @LastModifiedDate
    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second_millis)
    private LocalDateTime updated;

    @Override
    public boolean isNew() {
        return true;
    }
}
