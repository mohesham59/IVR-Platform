# IVR Platform - Quick Start Guide

## ✅ What Has Been Created

### Stage 1 Foundation Classes
Four core classes have been implemented with **full Javadoc documentation**:

1. **VxmlLoader.java**
   - Discovers and loads VXML files from `scenarios/` directory
   - Thread-safe caching mechanism
   - Supports classpath and filesystem loading
   - Detailed inline documentation with usage examples

2. **VxmlValidator.java**
   - Validates VXML 2.1 structure and compliance
   - Extracts transfer destinations for chaining logic
   - Comprehensive error reporting
   - Inline documentation with examples

3. **VxmlConfig.java**
   - Centralized configuration management
   - Environment variable overrides support
   - Singleton pattern for app-wide access
   - Full Javadoc with examples

4. **VxmlSession.java**
   - Session context holder (one per call)
   - Tracks execution state, collected variables, timing
   - Enum for session states (RUNNING, COMPLETED, ERROR, TIMEOUT)
   - Comprehensive documentation

### Documentation
- **IVR_IMPLEMENTATION_PLAN.md** (12+ sections)
  - Complete 8-stage roadmap with timelines
  - Architecture overview with ASCII diagrams
  - Testing strategy (unit, integration, acceptance)
  - Configuration and deployment guides
  - Common pitfalls and solutions

---

## 🚀 How to Test Stage 1

### Prerequisites
```bash
cd /home/omar/windows/D/omar/Telecom_ITI/500_GarduationProject/Ivr_project/IVR-Platform/IVR-engine

# Ensure Maven is installed
mvn --version

# Compile the new classes
mvn clean compile
```

### Quick Test (Command Line)

Create a simple test file to verify the foundation works:

```bash
# Create a temporary test script
cat > /tmp/TestVxmlLoader.java << 'EOF'
import gov.iti.telecom.*;
import org.w3c.dom.Document;

public class TestVxmlLoader {
    public static void main(String[] args) throws Exception {
        // Test 1: Load hello.vxml
        VxmlLoader loader = new VxmlLoader("scenarios/");
        System.out.println("\n=== Test 1: Loading hello.vxml ===");
        Document doc = loader.loadVxml("hello");
        System.out.println("✓ Loaded successfully");
        
        // Test 2: Validate the VXML
        System.out.println("\n=== Test 2: Validating VXML ===");
        VxmlValidator validator = new VxmlValidator();
        VxmlValidator.ValidationResult result = validator.validate(doc);
        System.out.println("Valid: " + result.isValid());
        if (!result.isValid()) {
            result.getErrors().forEach(e -> System.out.println("  - " + e.getMessage()));
        }
        
        // Test 3: List available VXML files
        System.out.println("\n=== Test 3: Available VXML Files ===");
        loader.listAvailableVxml().forEach(f -> System.out.println("  - " + f));
        
        // Test 4: Create a session
        System.out.println("\n=== Test 4: Creating Session ===");
        VxmlSession session = new VxmlSession("call-001", "hello.vxml");
        session.setState(VxmlSession.SessionState.RUNNING);
        session.setVariable("user_choice", "1");
        System.out.println("Session: " + session.toString());
        
        // Test 5: Load configuration
        System.out.println("\n=== Test 5: Configuration ===");
        VxmlConfig config = VxmlConfig.loadFromClasspath();
        System.out.println("Config: " + config.toString());
    }
}
EOF

# Run the test (requires compiled classes in classpath)
# javac -cp target/classes /tmp/TestVxmlLoader.java
# java -cp target/classes:/tmp TestVxmlLoader
```

### Verify Files Exist
```bash
# Check that all new classes were created
ls -la src/main/java/gov/iti/telecom/Vxml*.java

# Expected output:
# VxmlConfig.java
# VxmlLoader.java
# VxmlSession.java
# VxmlValidator.java
```

---

## 📝 Next Immediate Steps

### Week 1: Complete Stage 1 Testing

#### 1. Create Unit Tests (5-10 tests per class)
```
src/test/java/gov/iti/telecom/
├── VxmlLoaderTest.java       (NEW)
├── VxmlValidatorTest.java    (NEW)
├── VxmlConfigTest.java       (NEW)
└── VxmlSessionTest.java      (NEW)
```

**Sample test structure**:
```java
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class VxmlLoaderTest {
    
    @Test
    void testLoadSimpleVxml() throws Exception {
        VxmlLoader loader = new VxmlLoader("scenarios/");
        Document doc = loader.loadVxml("hello");
        assertNotNull(doc);
        assertEquals("vxml", doc.getDocumentElement().getLocalName());
    }
    
    @Test
    void testVxmlNotFound() {
        VxmlLoader loader = new VxmlLoader("scenarios/");
        assertThrows(RuntimeException.class, () -> 
            loader.loadVxml("nonexistent-vxml")
        );
    }
    
    @Test
    void testCaching() throws Exception {
        VxmlLoader loader = new VxmlLoader("scenarios/");
        loader.setCachingEnabled(true);
        Document doc1 = loader.loadVxml("hello");
        Document doc2 = loader.loadVxml("hello");
        assertEquals(1, loader.getCacheSize());
    }
}
```

**Commands**:
```bash
# Add JUnit dependency to pom.xml (see section below)
mvn test                    # Run all tests
mvn test -Dtest=VxmlLoaderTest    # Run specific test
mvn test jacoco:report      # Generate coverage report
```

#### 2. Update pom.xml - Add Testing Dependencies
Add these dependencies inside `<dependencies>` section:

```xml
<!-- Testing -->
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

#### 3. Create Configuration File
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
asterisk.sip.port=5060

# TTS Configuration
tts.provider=google
tts.language=en-US
tts.temp.dir=/tmp/ivr-tts

# Validation
vxml.validate.on.load=true
```

#### 4. Test Sample VXML Files
Create additional test VXML files in `scenarios/`:

**File**: `scenarios/menu-example.vxml`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
    <form id="mainMenu">
        <menu>
            <prompt>
                Welcome to the menu. Press 1 for reservations, 2 for information, 0 for operator.
            </prompt>
            <choice dtmf="1" next="#reservations" />
            <choice dtmf="2" next="#info" />
            <choice dtmf="0" next="#operator" />
        </menu>
        
        <form id="reservations">
            <block>
                <prompt>You selected reservations.</prompt>
            </block>
        </form>
        
        <form id="info">
            <block>
                <prompt>You selected information.</prompt>
            </block>
        </form>
        
        <form id="operator">
            <transfer dest="sip:operator@example.com" />
        </form>
    </form>
</vxml>
```

**File**: `scenarios/transfer-example.vxml`
```xml
<?xml version="1.0" encoding="UTF-8"?>
<vxml version="2.1" xmlns="http://www.w3.org/2001/vxml">
    <form id="transferTest">
        <block>
            <prompt>Transferring you to the next menu.</prompt>
            <transfer dest="file:///scenarios/menu-example.vxml" />
        </block>
    </form>
</vxml>
```

---

## 🔗 Code Navigation

### How to Find and Understand Code

1. **VxmlLoader** - Start here to understand resource discovery:
   - Main method: `loadVxml(String vxmlName)`
   - Key concept: ConcurrentHashMap caching
   - Location: `src/main/java/gov/iti/telecom/VxmlLoader.java`

2. **VxmlValidator** - Then understand validation:
   - Main method: `validate(Document doc)`
   - Key concept: XML DOM traversal
   - Returns: `ValidationResult` with error list

3. **VxmlSession** - Then understand session lifecycle:
   - Key concept: Session states (enum)
   - Variables storage: `Map<String, Object>`
   - Timing: `createdAt`, `getDurationSeconds()`

4. **VxmlConfig** - Finally understand configuration:
   - Key concept: Singleton + environment overrides
   - Pattern: getProperty() with fallback

### Documentation Markers in Code
Look for these in every class:
- `<h2>HOW IT WORKS</h2>` - Step-by-step explanation
- `<h2>USAGE EXAMPLE</h2>` - Copy-paste ready code
- `@param`, `@return`, `@throws` - Method documentation
- Inline comments with "WHY" not just "WHAT"

---

## 📊 Architecture at a Glance

```
┌─ VxmlLoader ─────────────────────┐
│ Find & Load .vxml files          │
│ Cache in ConcurrentHashMap       │
│ Thread-safe access               │
└──────────┬──────────────────────┘
           │
           ↓
┌─ VxmlValidator ───────────────────┐
│ Validate XML structure            │
│ Extract transfer destinations     │
│ Collect errors for reporting      │
└──────────┬──────────────────────┘
           │
           ↓
┌─ VxmlScenarioEngine (NEXT) ─────┐
│ Execute VXML with JVoiceXML      │
│ Manage VxmlSession lifecycle     │
│ Handle state transitions         │
└──────────┬──────────────────────┘
           │
           ↓
┌─ VxmlSessionBridge (NEXT) ───────┐
│ Chain VXML → VXML                │
│ Transfer to API endpoints         │
│ Inherit variables                │
└──────────────────────────────────┘
```

---

## 🐛 Troubleshooting

| Issue | Cause | Solution |
|-------|-------|----------|
| `VxmlLoader not found` | Classpath issue | Run `mvn clean compile` first |
| `hello.vxml not found` | Wrong resource path | Check `VxmlConfig.getVxmlResourcePath()` |
| `XML namespace error` | Missing xmlns | Ensure VXML has `xmlns="http://www.w3.org/2001/vxml"` |
| Test fails | JUnit not in classpath | Add dependencies to pom.xml |
| Cache not working | Caching disabled | Call `loader.setCachingEnabled(true)` |

---

## 📚 Learning Resources

### Recommended Reading Order
1. **VxmlSession** - Simplest, understand session concept
2. **VxmlConfig** - Understand singleton & properties pattern
3. **VxmlLoader** - Understand file I/O & caching
4. **VxmlValidator** - Understand XML processing

### External References
- [VXML 2.1 Spec](https://www.w3.org/TR/voicexml21/)
- [JVoiceXML Docs](https://jvoicexml.sourceforge.net/)
- [W3C XML Namespaces](https://www.w3.org/TR/xml-names/)

---

## ✨ Code Quality Standards

All code follows these standards:

1. **Javadoc**: Every public class and method documented
2. **Examples**: Usage examples in Javadoc `<h2>USAGE EXAMPLE</h2>`
3. **Null Safety**: Null checks with clear error messages
4. **Thread Safety**: Thread-safety documented
5. **Logging**: System.out.println + error.println for debugging

---

## 🎯 Success Checklist

- [ ] All 4 foundation classes compiled successfully
- [ ] `mvn clean compile` runs without errors
- [ ] Can load `hello.vxml` with `VxmlLoader`
- [ ] `VxmlValidator.validate()` runs without crashes
- [ ] `VxmlSession` can store/retrieve variables
- [ ] `VxmlConfig` loads defaults correctly
- [ ] Unit tests created and passing (>80% coverage)
- [ ] Configuration file created in resources/
- [ ] Additional test VXML files created (menu, transfer examples)

---

## 📞 Support

**Questions about:**
- **Architecture**: See `IVR_IMPLEMENTATION_PLAN.md` sections 2-3
- **Testing**: See `IVR_IMPLEMENTATION_PLAN.md` section 6
- **Code**: Check Javadoc in each class
- **Configuration**: See `VxmlConfig` class documentation

---

**Document Version**: 1.0  
**Date**: 2026-07-24  
**Next Review**: After Stage 1 testing completion
