package com.joinai_support.domain;

import com.joinai_support.utils.Gender;
import com.joinai_support.utils.Role;
import jakarta.persistence.PrePersist;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "users")
public class User  {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String password;
    private String email;
    private Role role;
    private LocalDate createdAt = LocalDate.now();
    private Boolean enabled = Boolean.TRUE;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDate.now();
        }
        if (enabled == null) {
            enabled = Boolean.TRUE;
        }
    }

}
