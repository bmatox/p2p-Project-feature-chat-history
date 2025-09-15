package org.unifor; // Ou seu pacote principal do projeto

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Ponto de entrada da aplicação web Spring Boot.
 * A anotação @SpringBootApplication ativa a auto-configuração, a varredura
 * de componentes e inicia o servidor web embutido.
 */
@SpringBootApplication
public class Main {

    public static void main(String[] args) {
        // Esta única linha é responsável por iniciar toda a aplicação Spring:
        // 1. Inicia um servidor web (Tomcat) embutido.
        // 2. Procura por componentes (@Service, @Controller) e os inicializa.
        // 3. Deixa a aplicação pronta para receber requisições web.
        SpringApplication.run(Main.class, args);
    }
}