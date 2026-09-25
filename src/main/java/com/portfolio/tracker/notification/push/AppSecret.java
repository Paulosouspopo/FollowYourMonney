package com.portfolio.tracker.notification.push;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Secret généré par l'application et conservé en base (ex : clés VAPID). */
@Entity
@Table(name = "app_secrets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AppSecret {

    @Id
    private String name;

    @Column(nullable = false, length = 2000)
    private String value;
}
