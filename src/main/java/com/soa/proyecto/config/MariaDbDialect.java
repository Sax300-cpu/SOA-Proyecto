package com.soa.proyecto.config;

import org.hibernate.dialect.MariaDBDialect;

public class MariaDbDialect extends MariaDBDialect {

    @Override
    public boolean supportsIfExistsAfterAlterTable() {
        // XAMPP con MariaDB 10.4 no admite ALTER TABLE IF EXISTS.
        return false;
    }
}
