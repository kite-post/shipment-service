package ua.kite.shipment;

import org.springframework.boot.SpringApplication;

/**
 * `make run`: той самий застосунок, але Postgres і Kafka піднімаються
 * контейнерами з {@link TestcontainersConfiguration}. Стенд не потрібен,
 * порти з ним не конфліктують. Запуск з IDE — просто main цього класу.
 */
public class ShipmentDevApplication {

    public static void main(String[] args) {
        SpringApplication.from(ShipmentApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
