package edu.unisabana.tyvs.registry.application.usecase;

import edu.unisabana.tyvs.registry.application.port.out.RegistryRepositoryPort;
import edu.unisabana.tyvs.registry.domain.model.Gender;
import edu.unisabana.tyvs.registry.domain.model.Person;
import edu.unisabana.tyvs.registry.domain.model.RegisterResult;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Clase de prueba unitaria para {@link Registry} utilizando un mock de {@link RegistryRepositoryPort}.
 *
 * <p>Estas pruebas ilustran cómo aislar el caso de uso del repositorio real,
 * aplicando dobles de prueba (Mockito) para simular los escenarios.</p>
 *
 * <p><b>Formato AAA:</b></p>
 * <ul>
 *   <li><b>Arrange</b>: se preparan datos y comportamiento del mock.</li>
 *   <li><b>Act</b>: se ejecuta el método bajo prueba.</li>
 *   <li><b>Assert</b>: se verifican resultados y que no haya interacciones no deseadas.</li>
 * </ul>
 *
 * <p><b>Beneficio:</b> este tipo de prueba es una <i>unitaria pura</i>,
 * sin necesidad de levantar bases de datos ni infraestructura adicional.</p>
 */
public class RegistryWithMockTest {

    /** Mock del puerto de persistencia. */
    private RegistryRepositoryPort repo;

    /** Caso de uso bajo prueba, instanciado con el mock. */
    private Registry registry;

    /**
     * Configura el mock y el caso de uso antes de cada prueba.
     *
     * <p>Se crea un mock de {@link RegistryRepositoryPort} usando Mockito
     * y se inyecta en la instancia de {@link Registry}.</p>
     */
    @Before
    public void setUp() {
        repo = mock(RegistryRepositoryPort.class);
        registry = new Registry(repo);
    }

    /**
     * Caso de prueba: detectar registros duplicados.
     *
     * <p><b>Escenario (BDD):</b></p>
     * <ul>
     *   <li><b>Given</b>: una persona con ID=7 y el repositorio ya indica que ese ID existe.</li>
     *   <li><b>When</b>: se intenta registrar la persona.</li>
     *   <li><b>Then</b>: el resultado debe ser {@link RegisterResult#DUPLICATED}
     *       y no se debe invocar el método {@code save(...)} en el repositorio.</li>
     * </ul>
     *
     * @throws Exception propagada en caso de error durante la ejecución.
     */
    @Test
    public void shouldReturnDuplicatedWhenRepoSaysExists() throws Exception {
        // Arrange: configurar mock y datos
        when(repo.existsById(7)).thenReturn(true);
        Person p = new Person("Ana", 7, 25, Gender.FEMALE, true);

        // Act: ejecutar método bajo prueba
        RegisterResult result = registry.registerVoter(p);

        // Assert: verificar resultado y comportamiento esperado del mock
        assertEquals(RegisterResult.DUPLICATED, result);
        verify(repo, never()).save(anyInt(), anyString(), anyInt(), anyBoolean());
    }

    /**
     * Caso de prueba: persona valida que si se persiste.
     *
     * <p><b>Given</b> el repositorio dice que el ID no existe;
     * <b>When</b> se registra la persona;
     * <b>Then</b> el resultado es {@link RegisterResult#VALID} y se invoca
     * {@code save(...)} exactamente una vez con esos datos.</p>
     *
     * <p>Note la diferencia con la aserción de estado: aqui no verificamos que
     * el dato quedo guardado (no hay base de datos), sino que el caso de uso
     * <i>colaboro</i> correctamente con su puerto. Eso es una aserción de
     * comportamiento, y es lo unico que un mock puede darnos.</p>
     */
    @Test
    public void shouldSaveWhenPersonIsValid() throws Exception {
        // Arrange
        when(repo.existsById(8)).thenReturn(false);
        Person p = new Person("Luis", 8, 30, Gender.MALE, true);

        // Act
        RegisterResult result = registry.registerVoter(p);

        // Assert
        assertEquals(RegisterResult.VALID, result);
        verify(repo, times(1)).save(8, "Luis", 30, true);
    }

    /**
     * Caso de prueba: fallo de infraestructura.
     *
     * <p><b>Given</b> el repositorio lanza una {@link java.sql.SQLException};
     * <b>When</b> se intenta registrar; <b>Then</b> el caso de uso la traduce a
     * {@link RegistryPersistenceException} en vez de dejarla escapar.</p>
     *
     * <p>Este escenario es practicamente imposible de provocar con una base de
     * datos real: es el ejemplo canonico de para que sirve un mock.</p>
     */
    @Test
    public void shouldWrapPersistenceFailure() throws Exception {
        // Arrange: el puerto falla al consultar
        when(repo.existsById(9)).thenThrow(new java.sql.SQLException("conexion perdida"));
        Person p = new Person("Eva", 9, 30, Gender.FEMALE, true);

        // Act + Assert
        try {
            registry.registerVoter(p);
            fail("Se esperaba RegistryPersistenceException");
        } catch (RegistryPersistenceException expected) {
            assertEquals(java.sql.SQLException.class, expected.getCause().getClass());
        }
    }

    /**
     * Caso de prueba: reglas de dominio que ni siquiera tocan el repositorio.
     *
     * <p>Verificamos ademas que NO se consulta la base de datos: rechazar a un
     * menor de edad no deberia costar una consulta.</p>
     */
    @Test
    public void shouldRejectUnderageWithoutTouchingRepository() throws Exception {
        // Arrange
        Person menor = new Person("Sara", 10, 17, Gender.FEMALE, true);

        // Act
        RegisterResult result = registry.registerVoter(menor);

        // Assert
        assertEquals(RegisterResult.UNDERAGE, result);
        verifyNoInteractions(repo);
    }

    /** Persona nula: validacion defensiva, sin tocar el repositorio. */
    @Test
    public void shouldReturnInvalidWhenPersonIsNull() {
        assertEquals(RegisterResult.INVALID, registry.registerVoter(null));
        verifyNoInteractions(repo);
    }

    /** Documento no positivo: entrada invalida. */
    @Test
    public void shouldReturnInvalidWhenIdIsNotPositive() {
        Person p = new Person("Nadie", 0, 30, Gender.UNIDENTIFIED, true);

        assertEquals(RegisterResult.INVALID, registry.registerVoter(p));
        verifyNoInteractions(repo);
    }

    /** Persona no viva: se rechaza antes de evaluar la edad. */
    @Test
    public void shouldReturnDeadWhenPersonIsNotAlive() {
        Person p = new Person("Pedro", 11, 50, Gender.MALE, false);

        assertEquals(RegisterResult.DEAD, registry.registerVoter(p));
        verifyNoInteractions(repo);
    }

    /**
     * Edad negativa: dato IMPOSIBLE, no persona menor.
     *
     * <p>Es el caso que documenta el Defecto 01 de {@code defectos.md}. Antes
     * devolvia {@link RegisterResult#UNDERAGE}, igual que una edad de 17, y esa
     * confusion tiene consecuencias reales: a quien tiene 17 se le dice que
     * espere; a quien aparece con -1 hay que corregirle el registro.</p>
     */
    @Test
    public void shouldReturnInvalidAgeWhenAgeIsNegative() {
        Person p = new Person("Imposible", 12, -1, Gender.UNIDENTIFIED, true);

        assertEquals(RegisterResult.INVALID_AGE, registry.registerVoter(p));
        verifyNoInteractions(repo);
    }

    /** Edad por encima del maximo biologico: tambien es un dato imposible. */
    @Test
    public void shouldReturnInvalidAgeWhenAgeExceedsMaximum() {
        Person p = new Person("Matusalen", 13, Registry.MAX_AGE + 1, Gender.MALE, true);

        assertEquals(RegisterResult.INVALID_AGE, registry.registerVoter(p));
        verifyNoInteractions(repo);
    }

    /**
     * VALOR LIMITE de la frontera entre UNDERAGE e INVALID_AGE.
     *
     * <p>La edad 0 es el borde exacto: un ano menos es imposible, y 0 es un
     * dato correcto de un recien nacido que, evidentemente, no puede votar.
     * Sin esta prueba, cambiar {@code < 0} por {@code <= 0} en el codigo no
     * rompe nada, y esa mutacion sobrevive.</p>
     */
    @Test
    public void shouldReturnUnderageWhenAgeIsZero() {
        Person p = new Person("Recien nacida", 14, 0, Gender.FEMALE, true);

        assertEquals(RegisterResult.UNDERAGE, registry.registerVoter(p));
        verifyNoInteractions(repo);
    }

    /** VALOR LIMITE superior: la edad maxima exacta sigue siendo valida. */
    @Test
    public void shouldAcceptTheMaximumAge() throws Exception {
        Person p = new Person("Centenaria", 15, Registry.MAX_AGE, Gender.FEMALE, true);
        when(repo.existsById(15)).thenReturn(false);

        assertEquals(RegisterResult.VALID, registry.registerVoter(p));
        verify(repo).save(15, "Centenaria", Registry.MAX_AGE, true);
    }


    /**
     * Caso de prueba: fallo de infraestructura al PERSISTIR (no al consultar).
     * antes se había probado cuando se fallaba al consultar, el id, por ejemplo.
     * Sin embargo, ahora se prueba cuando la db arroja error al guardar.
     */
    @Test
    public void shouldWrapPersistenceFailureWhenSaveFails() throws Exception {
        // Arrange: existsById pasa, pero save() falla
        when(repo.existsById(16)).thenReturn(false);
        doThrow(new java.sql.SQLException("violacion de restriccion"))
                .when(repo).save(16, "Omar", 30, true);
        Person p = new Person("Omar", 16, 30, Gender.MALE, true);

        // Act y Assert
        try {
            registry.registerVoter(p);
            fail("Se esperaba RegistryPersistenceException");
        } catch (RegistryPersistenceException expected) {
            assertEquals(java.sql.SQLException.class, expected.getCause().getClass());
        }
        verify(repo).save(16, "Omar", 30, true);
    }
}
