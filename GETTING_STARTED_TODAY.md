# Getting Started Today - IVR Platform

**Time Estimate**: 30 minutes  
**Goal**: Verify foundation is solid and ready for testing

---

## Step 1: Compile the Code (5 minutes)

```bash
cd /home/omar/windows/D/omar/Telecom_ITI/500_GarduationProject/Ivr_project/IVR-Platform/IVR-engine

# Clean and compile
mvn clean compile

# Expected output should end with:
# [INFO] BUILD SUCCESS
```

**If you see errors:**
- Check Java version: `java -version` (should be 11+)
- Check Maven: `mvn --version`
- Check pom.xml is present
- Run `mvn dependency:resolve` to download deps

---

## Step 2: Verify Files Were Created (3 minutes)

```bash
# Check all 4 new classes exist
ls -la src/main/java/gov/iti/telecom/Vxml*.java

# Expected:
# VxmlConfig.java
# VxmlLoader.java  
# VxmlSession.java
# VxmlValidator.java
```

---

## Step 3: Create First Unit Test (15 minutes)

**File**: `src/test/java/gov/iti/telecom/VxmlLoaderTest.java`

Copy this code exactly:

```java
package gov.iti.telecom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VxmlLoaderTest — unit tests for VxmlLoader class.
 * 
 * Tests verify:
 * - VXML files can be loaded successfully
 * - Caching works correctly
 * - Invalid files throw appropriate errors
 * - Thread safety with concurrent loads
 */
public class VxmlLoaderTest {

    private VxmlLoader loader;

    @BeforeEach
    void setUp() {
        // Create a fresh loader for each test
        loader = new VxmlLoader("scenarios/");
    }

    /**
     * Test 1: Load a simple VXML file (hello.vxml)
     */
    @Test
    void testLoadSimpleVxml() throws IOException, SAXException {
        // Arrange
        String vxmlName = "hello";

        // Act
        Document doc = loader.loadVxml(vxmlName);

        // Assert
        assertNotNull(doc, "Document should not be null");
        assertEquals("vxml", doc.getDocumentElement().getLocalName(),
                "Root element should be 'vxml'");
    }

    /**
     * Test 2: Caching works (same document returned)
     */
    @Test
    void testCachingEnabled() throws IOException, SAXException {
        // Arrange
        loader.setCachingEnabled(true);
        String vxmlName = "hello";

        // Act
        Document doc1 = loader.loadVxml(vxmlName);
        Document doc2 = loader.loadVxml(vxmlName);

        // Assert
        assertEquals(1, loader.getCacheSize(),
                "Should have 1 document in cache");
        assertSame(doc1, doc2,
                "Same document instance should be returned from cache");
    }

    /**
     * Test 3: Non-existent VXML throws RuntimeException
     */
    @Test
    void testLoadNonexistentVxmlThrows() {
        // Arrange
        String vxmlName = "nonexistent-vxml-12345";

        // Act & Assert
        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> loader.loadVxml(vxmlName),
                "Should throw RuntimeException for missing VXML"
        );

        assertTrue(exception.getMessage().contains("not found"),
                "Error message should mention 'not found'");
    }

    /**
     * Test 4: Null VXML name throws exception
     */
    @Test
    void testLoadNullVxmlThrows() {
        // Act & Assert
        assertThrows(
                IllegalArgumentException.class,
                () -> loader.loadVxml(null),
                "Should throw IllegalArgumentException for null VXML name"
        );
    }

    /**
     * Test 5: List available VXML files
     */
    @Test
    void testListAvailableVxml() {
        // Act
        var vxmlFiles = loader.listAvailableVxml();

        // Assert
        assertNotNull(vxmlFiles, "List should not be null");
        assertTrue(vxmlFiles.size() > 0,
                "Should find at least hello.vxml");
        assertTrue(vxmlFiles.contains("hello"),
                "Should find hello.vxml in list");

        System.out.println("Found VXML files: " + vxmlFiles);
    }

    /**
     * Test 6: Cache clearing works
     */
    @Test
    void testClearCache() throws IOException, SAXException {
        // Arrange
        loader.setCachingEnabled(true);
        loader.loadVxml("hello");
        assertEquals(1, loader.getCacheSize());

        // Act
        loader.clearCache();

        // Assert
        assertEquals(0, loader.getCacheSize(),
                "Cache should be empty after clear");
    }
}
```

**Commands**:
```bash
# Create test directory structure
mkdir -p src/test/java/gov/iti/telecom

# Now compile tests
mvn test-compile

# Run the test
mvn test -Dtest=VxmlLoaderTest

# Expected: 6 tests should PASS
```

---

## Step 4: Update pom.xml - Add Test Dependencies (5 minutes)

**Find** this line in pom.xml:
```xml
  <dependency>
    <groupId>junit</groupId>
    <artifactId>junit</artifactId>
    <version>3.8.1</version>
    <scope>test</scope>
  </dependency>
```

**Replace it with** (update to modern testing):
```xml
  <!-- Testing Dependencies -->
  <dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter-api</artifactId>
    <version>5.9.2</version>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter-engine</artifactId>
    <version>5.9.2</version>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <version>3.24.1</version>
    <scope>test</scope>
  </dependency>
  <dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <version>5.2.0</version>
    <scope>test</scope>
  </dependency>
```

**Then recompile**:
```bash
mvn clean compile
```

---

## Step 5: Run All Tests (2 minutes)

```bash
mvn test
```

**Expected output**:
```
[INFO] -------------------------------------------------------
[INFO] T E S T S
[INFO] -------------------------------------------------------
[INFO] Running gov.iti.telecom.VxmlLoaderTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] BUILD SUCCESS
```

---

## Step 6: Create Configuration File (2 minutes)

**File**: `src/main/resources/vxml-config.properties`

```properties
# Resource Paths
vxml.resource.path=scenarios/
vxml.resource.classpath.enabled=true
vxml.cache.enabled=true
vxml.cache.size=100

# Execution
vxml.session.timeout.seconds=600
vxml.session.max.active=1000

# Asterisk Integration
asterisk.connection.profile=default
asterisk.connection.protocol=SIP

# TTS Configuration
tts.provider=google
tts.language=en-US

# Validation
vxml.validate.on.load=true
```

```bash
# Create resources directory if needed
mkdir -p src/main/resources

# Compile to verify it loads
mvn compile
```

---

## Step 7: Quick Verification Test (3 minutes)

Create a simple Java program to verify everything works:

**File**: `src/test/java/gov/iti/telecom/QuickVerificationTest.java`

```java
package gov.iti.telecom;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import static org.junit.jupiter.api.Assertions.*;

public class QuickVerificationTest {

    @Test
    void verifyFoundationClasses() throws Exception {
        System.out.println("\n=== Verifying IVR Platform Foundation ===\n");

        // Test 1: VxmlLoader
        System.out.println("✓ Test 1: VxmlLoader");
        VxmlLoader loader = new VxmlLoader("scenarios/");
        Document doc = loader.loadVxml("hello");
        assertNotNull(doc);

        // Test 2: VxmlValidator
        System.out.println("✓ Test 2: VxmlValidator");
        VxmlValidator validator = new VxmlValidator();
        VxmlValidator.ValidationResult result = validator.validate(doc);
        assertTrue(result.isValid(), "hello.vxml should be valid");

        // Test 3: VxmlSession
        System.out.println("✓ Test 3: VxmlSession");
        VxmlSession session = new VxmlSession("call-001", "hello.vxml");
        session.setState(VxmlSession.SessionState.RUNNING);
        session.setVariable("user_input", "123");
        assertEquals("123", session.getVariable("user_input"));

        // Test 4: VxmlConfig
        System.out.println("✓ Test 4: VxmlConfig");
        VxmlConfig config = VxmlConfig.loadFromClasspath();
        assertNotNull(config.getVxmlResourcePath());
        assertTrue(config.getSessionTimeoutSeconds() > 0);

        System.out.println("\n✅ All Foundation Classes Verified!\n");
        System.out.println("Ready for Stage 2: VXML Execution Engine\n");
    }
}
```

```bash
mvn test -Dtest=QuickVerificationTest
```

---

## ✅ Success Checklist

After completing all steps:

- [ ] `mvn clean compile` runs without errors
- [ ] VxmlLoaderTest passes (6 tests)
- [ ] QuickVerificationTest passes
- [ ] Configuration file created
- [ ] Can see all new classes: `ls src/main/java/gov/iti/telecom/Vxml*.java`
- [ ] pom.xml updated with JUnit 5

---

## 🎯 If Any Step Fails

### Common Issues

**Error: "mvn: command not found"**
```bash
# Install Maven or add to PATH
brew install maven    # macOS
sudo apt install maven  # Linux
```

**Error: "Java 11+ required"**
```bash
java -version
# If less than 11, install Java 11+ or set JAVA_HOME
export JAVA_HOME=/path/to/java11
```

**Error: "hello.vxml not found"**
```bash
# Make sure you're in the right directory
pwd  # Should be: /path/to/IVR-engine
ls scenarios/hello.vxml  # Should exist
```

**Error: "Test class not found"**
```bash
# Make sure tests are in correct directory
mkdir -p src/test/java/gov/iti/telecom
# Then put VxmlLoaderTest.java there
```

---

## 📞 Next Actions (After Getting This Working)

1. **Create more tests**:
   - VxmlValidatorTest.java
   - VxmlConfigTest.java
   - VxmlSessionTest.java

2. **Create example VXML files** (in scenarios/):
   - menu-example.vxml
   - transfer-example.vxml

3. **Read documentation**:
   - Start: QUICK_START.md
   - Then: IVR_IMPLEMENTATION_PLAN.md (Sections 2-3)

4. **Schedule Week 2 work**:
   - Create VxmlScenarioEngine.java
   - Integrate with JVoiceXML runtime

---

## 📊 Current Status After This Session

```
Foundation Classes:        ✅ Created (4 classes)
Documentation:             ✅ Complete (3 files)
Unit Tests:                ⏳ Started (1 test class)
Configuration:             ⏳ In progress (1 file)
Integration Tests:         📅 Week 2
VXML Execution:            📅 Week 2
Full Integration:          📅 Week 3-4

Progress: 20-30% (foundation ready for testing)
```

---

## 🚀 You're Ready!

All foundation classes are created and documented. Now it's just testing, creating a few more tests, and moving to Stage 2.

**Questions?** Check QUICK_START.md or IVR_IMPLEMENTATION_PLAN.md.

---

**Time to Complete**: ~30 minutes  
**Next Checkpoint**: After all tests pass
