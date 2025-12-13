package com.example.model;

import com.example.model.enums.JobStatus;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "action_jobs")
@Getter
@Setter
public class ActionJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(
            name = "created_at",
            columnDefinition = "TIMESTAMP DEFAULT now()",
            insertable = false,
            updatable = false
    )
    private LocalDateTime createdAt;

    @Column(name = "message_type", nullable = false)
    private String messageType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private JobStatus status;

    @Type(JsonBinaryType.class)
    @Column(name = "data", columnDefinition = "jsonb")
    private String data;
  
}
