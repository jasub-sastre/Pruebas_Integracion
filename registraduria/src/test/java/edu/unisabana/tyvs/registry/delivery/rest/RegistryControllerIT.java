package edu.unisabana.tyvs.registry.delivery.rest;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * PRUEBA DE SISTEMA (caja negra): levanta la aplicacion COMPLETA en un puerto
 * aleatorio y la ejercita por HTTP, igual que lo haria un cliente real.
 *
 * Puntos de diseno que conviene notar:
 *
 * 1. NO define beans propios. El cableado real vive en RegistryConfig; si la
 *    prueba lo duplicara, estaria probando su propio cableado y no el de
 *    produccion (ademas de romper el contexto por nombres de bean repetidos).
 *
 * 2. Usa su PROPIA base de datos (registry.jdbc-url) para no compartir estado
 *    con las demas pruebas. H2 con DB_CLOSE_DELAY=-1 sobrevive mientras viva la
 *    JVM, y Surefire/Failsafe reutilizan la JVM entre clases: sin un nombre
 *    distinto, un id insertado aqui reaparece en otra prueba.
 *
 * 3. RANDOM_PORT evita el puerto fijo, que haria fallar la prueba si algo mas
 *    esta escuchando en 8080.
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "registry.jdbc-url=jdbc:h2:mem:regdb_ctrl_it;DB_CLOSE_DELAY=-1")
public class RegistryControllerIT {

    @Autowired
    private TestRestTemplate rest;

    private ResponseEntity<String> register(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.postForEntity("/register", new HttpEntity<>(json, headers), String.class);
    }

    @Test
    public void shouldRegisterValidPerson() {
        // Arrange
        String json = "{\"name\":\"Ana\",\"id\":100,\"age\":30,\"gender\":\"FEMALE\",\"alive\":true}";

        // Act
        ResponseEntity<String> resp = register(json);

        // Assert: assertEquals, NO la palabra clave assert de Java.
        // 'assert' solo se evalua con -ea; el Test Runner de VS Code no lo activa
        // y la prueba pasaria SIEMPRE, incluso con la respuesta equivocada.
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("VALID", resp.getBody());
    }

    @Test
    public void shouldReturnDuplicatedWhenIdAlreadyRegistered() {
        // Arrange: el primer registro deja el id 101 ocupado
        String json = "{\"name\":\"Luis\",\"id\":101,\"age\":40,\"gender\":\"MALE\",\"alive\":true}";
        assertEquals("VALID", register(json).getBody());

        // Act: el mismo id, otra persona
        ResponseEntity<String> resp = register(
                "{\"name\":\"Luisa\",\"id\":101,\"age\":35,\"gender\":\"FEMALE\",\"alive\":true}");

        // Assert
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("DUPLICATED", resp.getBody());
    }

    @Test
    public void shouldReturnUnderageWhenPersonIsMinor() {
        // Arrange + Act
        ResponseEntity<String> resp = register(
                "{\"name\":\"Sara\",\"id\":102,\"age\":17,\"gender\":\"FEMALE\",\"alive\":true}");

        // Assert
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("UNDERAGE", resp.getBody());
    }

    @Test
    public void shouldReturnDeadWhenPersonIsNotAlive() {
        // Arrange + Act
        ResponseEntity<String> resp = register(
                "{\"name\":\"Pedro\",\"id\":103,\"age\":50,\"gender\":\"MALE\",\"alive\":false}");

        // Assert
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("DEAD", resp.getBody());
    }

    @Test
    public void shouldReturnInvalidAgeWhenAgeIsImpossible() {
        // Arrange: 121 anios esta fuera del rango biologicamente posible (INVALID_AGE),
        // que es una clase de equivalencia distinta de UNDERAGE.
        ResponseEntity<String> resp = register(
                "{\"name\":\"Old\",\"id\":105,\"age\":121,\"gender\":\"MALE\",\"alive\":true}");

        // Assert
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("INVALID_AGE", resp.getBody());
    }

    @Test
    public void shouldReturnInvalidWhenIdIsNotPositive() {
        // Arrange: id <= 0 es un dato de persona invalido segun el dominio,
        // pero sigue siendo un JSON bien formado -> el caso de uso responde
        // INVALID con 200, no es un error HTTP.
        ResponseEntity<String> resp = register(
                "{\"name\":\"Nadie\",\"id\":0,\"age\":30,\"gender\":\"MALE\",\"alive\":true}");

        // Assert
        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("INVALID", resp.getBody());
    }

    @Test
    public void shouldReturnBadRequestWhenGenderIsNotValid() {
        // 'X' no es un valor del enum Gender.
        // Sin manejo de error esto seria un 500; el @RestControllerAdvice lo
        // convierte en el 400 que corresponde a una entrada invalida del cliente.
        ResponseEntity<String> resp = register(
                "{\"name\":\"Eva\",\"id\":104,\"age\":30,\"gender\":\"X\",\"alive\":true}");

        // Assert: status Y body, para confirmar que paso por
        // handleInvalidArgument() y no por otro handler.
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("INVALID_INPUT", resp.getBody());
    }

    @Test
    public void shouldReturnBadRequestWhenJsonIsMalformed() {
        // Arrange: JSON con una coma de mas / sintaxis rota.
        // Ejercita handleMalformedJson(), la otra rama del RestControllerAdvice.
        String brokenJson = "{\"name\":\"Eva\",\"id\":106,\"age\":30,,}";

        // Act
        ResponseEntity<String> resp = register(brokenJson);

        // Assert
        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("MALFORMED_JSON", resp.getBody());
    }
}