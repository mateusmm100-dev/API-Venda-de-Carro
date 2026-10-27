package br.unifor.locadora.config;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:production-schema;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "gateway.shared-secret=production-schema-test-secret"
})
@ActiveProfiles("prod")
class ProductionSchemaMigrationTest {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void flywayCreatesSchemaThatHibernateCanValidate() {
        assertThat(entityManagerFactory.isOpen()).isTrue();
    }
}
