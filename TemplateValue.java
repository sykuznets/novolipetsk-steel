package com.example.api.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Entity
@Table(
        name = "template_values",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_template_id_manager_code",
                columnNames = {"template_id", "manager_code"}
        )
)
@NoArgsConstructor
@Setter
@Getter
@Accessors(chain = true)
public class TemplateValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, unique = true)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "template_id",
            referencedColumnName = "id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_template_values_templates")
    )
    private Template template;

    @Column(name = "manager_code", precision = 3, scale = 2, nullable = false)
    private BigDecimal managerCode;

    @Column(name = "amount", precision = 3, nullable = false)
    private BigDecimal amount;

    @Column(name = "order_number", nullable = false)
    private Integer orderNumber;
}
