package com.yohann.ocihelper.utils;

import com.yohann.ocihelper.bean.dto.ConsoleConnectionResultDTO;
import com.yohann.ocihelper.bean.dto.SshKeyPairDTO;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;


import com.oracle.bmc.core.ComputeClient;
import com.oracle.bmc.core.model.CreateInstanceConsoleConnectionDetails;
import com.oracle.bmc.core.model.Instance;
import com.oracle.bmc.core.model.InstanceConsoleConnection;
import com.oracle.bmc.core.requests.CreateInstanceConsoleConnectionRequest;
import com.oracle.bmc.core.requests.DeleteInstanceConsoleConnectionRequest;
import com.oracle.bmc.core.requests.GetInstanceConsoleConnectionRequest;
import com.oracle.bmc.core.requests.GetInstanceRequest;
import com.oracle.bmc.core.requests.ListInstanceConsoleConnectionsRequest;
import com.oracle.bmc.core.responses.CreateInstanceConsoleConnectionResponse;
import com.oracle.bmc.core.responses.GetInstanceConsoleConnectionResponse;
import com.oracle.bmc.core.responses.ListInstanceConsoleConnectionsResponse;
import com.oracle.bmc.model.BmcException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * @ClassName OciShellUtils
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-06-03 10:43
 **/
@Slf4j
@Builder
public class OciConsoleUtils {

    private static final int MAX_POLLING_ATTEMPTS = 15;
    private static final long POLLING_INTERVAL_MS = 5000L;
    private static final int MAX_DELETION_ATTEMPTS = 10;
    private static final long DELETION_POLLING_INTERVAL_MS = 3000L;

    ComputeClient computeClient;

    public void setComputeClient(ComputeClient computeClient) {
        this.computeClient = computeClient;
    }

    public OciConsoleUtils(ComputeClient computeClient) {
        this.computeClient = computeClient;
    }

    /**
     * Generates a new RSA SSH key pair (2048-bit).
     *
     * @return An SshKeyPair object containing public key (PEM, OpenSSH) and private key (PEM).
     * @throws Exception if key generation fails.
     */
    public static SshKeyPairDTO generateSshKeyPair() throws Exception {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(new RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4), new SecureRandom());
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            PublicKey publicKey = keyPair.getPublic();
            PrivateKey privateKey = keyPair.getPrivate();

            String publicKeyPem = convertPublicKeyToPem(publicKey);
            String publicKeyOpenSSH = convertPublicKeyToOpenSSH((RSAPublicKey) publicKey);
            String privateKeyPem = convertPrivateKeyToPem(privateKey);

            log.info("Successfully generated SSH key pair.");
            return new SshKeyPairDTO(publicKeyPem, privateKeyPem, publicKeyOpenSSH);
        } catch (NoSuchAlgorithmException e) {
            throw new Exception("RSA algorithm not available.", e);
        } catch (Exception e) {
            throw new Exception("Failed to generate SSH key pair.", e);
        }
    }

    /**
     * Saves the SSH key pair to files in the specified directory.
     *
     * @param keyPair       The SshKeyPair object to save.
     * @param keyName       The base name for the key files (e.g., "my-key" will create "my-key" and "my-key.pub").
     * @param saveDirectory The directory where the key files will be saved.
     * @return A map containing the paths to the saved private and public key files.
     * @throws IOException if an I/O error occurs during file writing.
     */
    public static Map<String, String> saveSshKeyPairToFiles(SshKeyPairDTO keyPair, String keyName, String saveDirectory) throws IOException {
        Map<String, String> keyFilePaths = new HashMap<>();
        File directory = new File(saveDirectory);
        if (!directory.exists()) {
            directory.mkdirs(); // Create directory if it doesn't exist
        }

        String privateKeyPath = saveDirectory + File.separator + keyName;
        try (FileWriter writer = new FileWriter(privateKeyPath)) {
            writer.write(keyPair.getPrivateKey());
        }
        keyFilePaths.put("privateKey", privateKeyPath);

        String publicKeyPath = saveDirectory + File.separator + keyName + ".pub";
        try (FileWriter writer = new FileWriter(publicKeyPath)) {
            writer.write(keyPair.getPublicKeyOpenSSH());
        }
        keyFilePaths.put("publicKey", publicKeyPath);

        // Set permissions for the private key file (read-only for owner)
        File privateKeyFile = new File(privateKeyPath);
        privateKeyFile.setReadable(false, false); // Not readable by others
        privateKeyFile.setReadable(true, true);  // Readable by owner
        privateKeyFile.setWritable(false, false); // Not writable by others
        privateKeyFile.setWritable(true, true); // Writable by owner
        privateKeyFile.setExecutable(false); // Not executable

        log.info("SSH key pair saved to: Private Key={}, Public Key={}", privateKeyPath, publicKeyPath);
        return keyFilePaths;
    }

    /**
     * Creates an OCI console connection for an instance, automatically generating an SSH key pair.
     *
     * @param instanceId  The OCID of the compute instance.
     * @param displayName A display name for the console connection.
     * @return A ConsoleConnectionResult object if successful, null otherwise.
     */
    public ConsoleConnectionResultDTO createConsoleConnectionWithAutoKey(String instanceId, String displayName) {
        try {
            SshKeyPairDTO sshKeyPair = generateSshKeyPair();
            log.info("Generated key info: OpenSSH Public Key (first 50 chars): {}", sshKeyPair.getPublicKeyOpenSSH().substring(0, Math.min(50, sshKeyPair.getPublicKeyOpenSSH().length())) + "...");

            String connectionId = createConsoleConnection(instanceId, sshKeyPair.getPublicKeyOpenSSH(), displayName);
            if (connectionId == null) {
                log.error("Failed to create console connection.");
                return null;
            }

            log.info("Console connection created successfully, ID: {}", connectionId);
            String connectionString = waitForConnectionAndGetDetails(connectionId, "connection");
            String vncConnectionString = waitForConnectionAndGetDetails(connectionId, "vnc");

            return new ConsoleConnectionResultDTO(connectionId, connectionString, vncConnectionString, sshKeyPair, true);
        } catch (Exception e) {
            log.error("Failed to create console connection (auto-key generation): {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Creates an OCI console connection for a given instance using a provided public key.
     *
     * @param instanceId  The OCID of the compute instance.
     * @param publicKey   The SSH public key in OpenSSH format.
     * @param displayName A display name for the console connection.
     * @return The ID of the created console connection if successful, null otherwise.
     */
    public String createConsoleConnection(String instanceId, String publicKey, String displayName) {
        log.info("🔑 Starting console connection creation for instance ID: {}", instanceId);
        log.info("  Display Name: {}", displayName);

        if (!isValidOpenSSHPublicKey(publicKey)) {
            log.error("❌ Invalid public key format! Expected OpenSSH format (ssh-rsa ...), Actual format: {}",
                    publicKey.startsWith("-----BEGIN") ? "PEM" : "Unknown");
            log.error("Oracle Cloud requires OpenSSH formatted public key. Example: ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAAB...");
            return null;
        }

        try {
            Instance instance = getInstanceIfExists(instanceId);
            if (instance == null) {
                log.error("❌ Instance {} does not exist or is inaccessible.", instanceId);
                return null;
            }
            log.info("✅ Instance validation successful: {}", instance.getDisplayName());

            // Check and delete any existing active connections for this instance
            String existingConnectionId = findExistingActiveConnection(instanceId, instance.getCompartmentId());
            if (existingConnectionId != null) {
                log.info("Detected existing active connection {}, attempting to delete it.", existingConnectionId);
                deleteConsoleConnection(existingConnectionId); // Use the public delete method
            }

            CreateInstanceConsoleConnectionDetails createDetails = CreateInstanceConsoleConnectionDetails.builder()
                    .instanceId(instanceId)
                    .publicKey(publicKey)
                    .build();
            CreateInstanceConsoleConnectionRequest createRequest = CreateInstanceConsoleConnectionRequest.builder()
                    .createInstanceConsoleConnectionDetails(createDetails)
                    .build();

            log.info("📡 Sending create console connection request...");
            CreateInstanceConsoleConnectionResponse response = computeClient.createInstanceConsoleConnection(createRequest);
            String connectionId = response.getInstanceConsoleConnection().getId();
            log.info("✅ Successfully created console connection. ID: {}, Initial State: {}", connectionId, response.getInstanceConsoleConnection().getLifecycleState());
            return connectionId;
        } catch (BmcException e) {
            log.error("❌ Failed to create console connection - OCI API Error:");
            log.error("  Status Code: {}", e.getStatusCode());
            log.error("  Service Code: {}", e.getServiceCode());
            log.error("  Error Message: {}", e.getMessage());
            log.error("  Request ID: {}", e.getOpcRequestId());
            if (e.getStatusCode() == 400 && e.getMessage().contains("Invalid ssh public key")) {
                log.error("  🔧 Public key format diagnostic: Detected PEM format public key! Please use OpenSSH format.");
            }
            return null;
        } catch (Exception e) {
            log.error("❌ An unexpected error occurred while creating console connection: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Validates if a given string is a valid OpenSSH public key.
     *
     * @param publicKey The public key string to validate.
     * @return true if the key is valid OpenSSH format, false otherwise.
     */
    private static boolean isValidOpenSSHPublicKey(String publicKey) {
        if (publicKey == null || publicKey.trim().isEmpty()) {
            return false;
        }
        String trimmedKey = publicKey.trim();
        String[] supportedKeyPrefixes = {"ssh-rsa", "ssh-dss", "ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384", "ecdsa-sha2-nistp521", "ssh-ed25519"};

        for (String prefix : supportedKeyPrefixes) {
            if (trimmedKey.startsWith(prefix + " ")) {
                String[] parts = trimmedKey.split("\\s+");
                if (parts.length >= 2 && parts[1].matches("[A-Za-z0-9+/]+=*")) {
                    log.debug("✅ Valid OpenSSH {} public key detected.", prefix);
                    return true;
                }
            }
        }
        log.warn("❌ Invalid OpenSSH public key format.");
        return false;
    }

    /**
     * Retrieves the details of a specific console connection.
     *
     * @param connectionId The OCID of the console connection.
     * @return The InstanceConsoleConnection object if found, null otherwise.
     */
    public InstanceConsoleConnection getConsoleConnectionDetails(String connectionId) {
        try {
            GetInstanceConsoleConnectionRequest request = GetInstanceConsoleConnectionRequest.builder()
                    .instanceConsoleConnectionId(connectionId)
                    .build();
            GetInstanceConsoleConnectionResponse response = computeClient.getInstanceConsoleConnection(request);
            InstanceConsoleConnection connection = response.getInstanceConsoleConnection();
            log.info("Successfully retrieved console connection details. ID: {}, State: {}", connectionId, connection.getLifecycleState());
            return connection;
        } catch (BmcException e) {
            if (e.getStatusCode() == 404) {
                log.warn("Console connection {} not found.", connectionId);
            } else {
                log.error("Failed to get console connection details. Status Code: {}, Error: {}", e.getStatusCode(), e.getMessage(), e);
            }
            return null;
        } catch (Exception e) {
            log.error("An error occurred while getting console connection details: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Retrieves the SSH connection string for an active console connection.
     *
     * @param connectionId The OCID of the console connection.
     * @return The SSH connection string if active, null otherwise.
     */
    public String getConsoleConnectionString(String connectionId) {
        InstanceConsoleConnection connection = getConsoleConnectionDetails(connectionId);
        return (connection != null && connection.getLifecycleState() == InstanceConsoleConnection.LifecycleState.Active) ?
                connection.getConnectionString() : null;
    }

    /**
     * Retrieves the VNC connection string for an active console connection.
     *
     * @param connectionId The OCID of the console connection.
     * @return The VNC connection string if active, null otherwise.
     */
    public String getVncConnectionString(String connectionId) {
        InstanceConsoleConnection connection = getConsoleConnectionDetails(connectionId);
        return (connection != null && connection.getLifecycleState() == InstanceConsoleConnection.LifecycleState.Active) ?
                connection.getVncConnectionString() : null;
    }

    /**
     * Lists all console connections for a given instance.
     *
     * @param instanceId The OCID of the compute instance.
     * @return A list of InstanceConsoleConnection objects, or an empty list if an error occurs.
     */
    public List<InstanceConsoleConnection> listConsoleConnections(String instanceId) {
        try {
            Instance instance = getInstanceIfExists(instanceId);
            if (instance == null) {
                log.error("Instance {} does not exist or is inaccessible.", instanceId);
                return new ArrayList<>();
            }

            ListInstanceConsoleConnectionsRequest request = ListInstanceConsoleConnectionsRequest.builder()
                    .compartmentId(instance.getCompartmentId())
                    .instanceId(instanceId)
                    .build();
            ListInstanceConsoleConnectionsResponse response = computeClient.listInstanceConsoleConnections(request);
            List<InstanceConsoleConnection> connections = response.getItems();
            log.info("Instance {} has {} console connections.", instanceId, connections.size());
            return connections;
        } catch (Exception e) {
            log.error("An error occurred while listing console connections: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Deletes a specific console connection.
     *
     * @param connectionId The OCID of the console connection to delete.
     * @return true if the connection was successfully deleted or already deleted, false otherwise.
     */
    public boolean deleteConsoleConnection(String connectionId) {
        try {
            InstanceConsoleConnection connection = getConsoleConnectionDetails(connectionId);
            if (connection == null || connection.getLifecycleState() == InstanceConsoleConnection.LifecycleState.Deleted) {
                log.info("Console connection {} does not exist or is already deleted, no action needed.", connectionId);
                return true;
            }

            DeleteInstanceConsoleConnectionRequest deleteRequest = DeleteInstanceConsoleConnectionRequest.builder()
                    .instanceConsoleConnectionId(connectionId)
                    .build();
            computeClient.deleteInstanceConsoleConnection(deleteRequest);

            boolean deleted = waitForConnectionDeletion(connectionId);
            if (deleted) {
                log.info("Successfully deleted console connection: {}", connectionId);
            } else {
                log.warn("Console connection deletion might not be complete. ID: {}", connectionId);
            }
            return deleted;
        } catch (BmcException e) {
            if (e.getStatusCode() == 404) {
                log.info("Console connection {} not found, assuming successful deletion.", connectionId);
                return true;
            }
            log.error("Failed to delete console connection. Status Code: {}, Error: {}", e.getStatusCode(), e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("An error occurred while deleting console connection: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Cleans up (deletes) all non-deleted console connections for a given instance.
     *
     * @param instanceId The OCID of the compute instance.
     * @return The number of console connections successfully cleaned up.
     */
    public int cleanupConsoleConnections(String instanceId) {
        List<InstanceConsoleConnection> connections = listConsoleConnections(instanceId);
        int cleanedUpCount = 0;
        for (InstanceConsoleConnection connection : connections) {
            if (connection.getLifecycleState() != InstanceConsoleConnection.LifecycleState.Deleted) {
                if (deleteConsoleConnection(connection.getId())) {
                    cleanedUpCount++;
                }
            }
        }
        log.info("Cleaned up {} console connections for instance {}.", cleanedUpCount, instanceId);
        return cleanedUpCount;
    }

    /**
     * Gets an existing active console connection or creates a new one,
     * potentially generating a new key pair if no public key is provided.
     *
     * @param instanceId  The OCID of the compute instance.
     * @param publicKey   Optional: The SSH public key in OpenSSH format. If null or empty, a new key pair will be generated.
     * @param displayName A display name for the console connection.
     * @return A ConsoleConnectionResultDTO object if successful, null otherwise.
     */
    public ConsoleConnectionResultDTO getOrCreateConsoleConnectionWithAutoKey(String instanceId, String publicKey, String displayName) {
        try {
            Instance instance = getInstanceIfExists(instanceId);
            if (instance == null) {
                return null;
            }

            // Attempt to find an existing active connection
            String existingConnectionId = findExistingActiveConnection(instanceId, instance.getCompartmentId());
            if (existingConnectionId != null) {
                log.info("♻️ Reusing existing console connection: {}", existingConnectionId);
                String connectionString = getConsoleConnectionString(existingConnectionId);
                String vncConnectionString = getVncConnectionString(existingConnectionId);
                return new ConsoleConnectionResultDTO(existingConnectionId, connectionString, vncConnectionString, null, false);
            }

            // If no existing connection, create a new one
            if (publicKey == null || publicKey.trim().isEmpty()) {
                log.info("🔑 No public key provided, automatically generating SSH key pair.");
                return createConsoleConnectionWithAutoKey(instanceId, displayName);
            } else {
                if (!isValidOpenSSHPublicKey(publicKey)) {
                    log.error("❌ Provided public key format is invalid, OpenSSH format required.");
                    return null;
                }
                String newConnectionId = createConsoleConnection(instanceId, publicKey, displayName);
                if (newConnectionId == null) {
                    return null;
                }
                String connectionString = waitForConnectionAndGetDetails(newConnectionId, "connection");
                String vncConnectionString = waitForConnectionAndGetDetails(newConnectionId, "vnc");
                return new ConsoleConnectionResultDTO(newConnectionId, connectionString, vncConnectionString, null, false);
            }
        } catch (Exception e) {
            log.error("❌ An error occurred while getting or creating console connection: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Debugs and logs various SSH key formats for diagnostic purposes.
     *
     * @param keyPair The SshKeyPair to debug.
     */
    public static void debugKeyFormats(SshKeyPairDTO keyPair) {
        log.info("🔍 SSH Key Format Debug Information:");
        log.info("=================================");
        String publicKeyPem = keyPair.getPublicKey();
        String publicKeyOpenSSH = keyPair.getPublicKeyOpenSSH();
        String privateKeyPem = keyPair.getPrivateKey();

        log.info("PEM Public Key:");
        log.info("  Length: {}", publicKeyPem.length());
        log.info("  Starts with: {}", publicKeyPem.substring(0, Math.min(50, publicKeyPem.length())));
        log.info("  Ends with: {}", publicKeyPem.substring(Math.max(0, publicKeyPem.length() - 50)));
        log.info("  Format Correct: {}", (publicKeyPem.startsWith("-----BEGIN PUBLIC KEY-----") && publicKeyPem.endsWith("-----END PUBLIC KEY-----")));

        log.info("OpenSSH Public Key:");
        log.info("  Length: {}", publicKeyOpenSSH.length());
        log.info("  Content: {}", publicKeyOpenSSH);
        log.info("  Format Correct: {}", isValidOpenSSHPublicKey(publicKeyOpenSSH));

        log.info("Private Key (PEM):");
        log.info("  Length: {}", privateKeyPem.length());
        log.info("  Starts with: {}", privateKeyPem.substring(0, Math.min(50, privateKeyPem.length())));
        log.info("  Format Correct: {}", (privateKeyPem.startsWith("-----BEGIN PRIVATE KEY-----") && privateKeyPem.endsWith("-----END PRIVATE KEY-----")));
        log.info("=================================");
        log.info("🎯 Oracle Console Connection should use: OpenSSH Public Key Format");
        log.info("🎯 SSH client connection should use: Private Key (PEM Format)");
    }

    /**
     * Creates a console connection with an auto-generated key and saves the key to files.
     *
     * @param instanceId    The OCID of the compute instance.
     * @param keyName       The base name for the key files. If null, a default name will be used.
     * @param saveDirectory The directory to save the key files.
     * @param displayName   A display name for the console connection.
     * @return A map containing operation success status, connection details, and key file paths.
     */
    public Map<String, Object> createConsoleConnectionAndSaveKey(String instanceId, String keyName, String saveDirectory, String displayName) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        try {
            ConsoleConnectionResultDTO consoleConnectionResult = createConsoleConnectionWithAutoKey(instanceId, displayName);
            if (consoleConnectionResult == null) {
                result.put("message", "Failed to create console connection.");
                return result;
            }

            if (consoleConnectionResult.isKeyGenerated() && consoleConnectionResult.getKeyPair() != null) {
                String actualKeyName = (keyName != null && !keyName.isEmpty()) ? keyName : ("console-key-" + System.currentTimeMillis());
                Map<String, String> keyFiles = saveSshKeyPairToFiles(consoleConnectionResult.getKeyPair(), actualKeyName, saveDirectory);
                result.put("keyFiles", keyFiles);
            }

            result.put("success", true);
            result.put("connectionId", consoleConnectionResult.getConnectionId());
            result.put("connectionString", consoleConnectionResult.getConnectionString());
            result.put("vncConnectionString", consoleConnectionResult.getVncConnectionString());
            result.put("keyGenerated", consoleConnectionResult.isKeyGenerated());
            if (consoleConnectionResult.getKeyPair() != null) {
                result.put("publicKeyOpenSSH", consoleConnectionResult.getKeyPair().getPublicKeyOpenSSH());
            }
            log.info("Successfully created console connection and saved key. Connection ID: {}", consoleConnectionResult.getConnectionId());
            return result;
        } catch (Exception e) {
            log.error("Failed to create console connection and save key: {}", e.getMessage(), e);
            result.put("message", "Operation failed: " + e.getMessage());
            return result;
        }
    }

    /**
     * Converts a Java PublicKey object to its PEM string format.
     *
     * @param publicKey The PublicKey object.
     * @return The PEM formatted public key string.
     * @throws Exception if conversion fails.
     */
    private static String convertPublicKeyToPem(PublicKey publicKey) throws Exception {
        byte[] encoded = publicKey.getEncoded();
        String base64Encoded = Base64.getEncoder().encodeToString(encoded);
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN PUBLIC KEY-----\n");
        for (int i = 0; i < base64Encoded.length(); i += 64) {
            sb.append(base64Encoded, i, Math.min(i + 64, base64Encoded.length())).append("\n");
        }
        sb.append("-----END PUBLIC KEY-----");
        return sb.toString();
    }

    /**
     * Converts a Java RSAPublicKey object to its OpenSSH string format.
     *
     * @param publicKey The RSAPublicKey object.
     * @return The OpenSSH formatted public key string.
     * @throws IOException if an I/O error occurs.
     */
    private static String convertPublicKeyToOpenSSH(RSAPublicKey publicKey) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] sshRsaBytes = "ssh-rsa".getBytes();
            writeBytes(baos, sshRsaBytes);
            writeBytes(baos, publicKey.getPublicExponent().toByteArray());
            writeBytes(baos, publicKey.getModulus().toByteArray());
            String base64Encoded = Base64.getEncoder().encodeToString(baos.toByteArray());
            return "ssh-rsa " + base64Encoded + " console-connection@oracle";
        }
    }

    /**
     * Converts a Java PrivateKey object to its PEM string format.
     *
     * @param privateKey The PrivateKey object.
     * @return The PEM formatted private key string.
     * @throws Exception if conversion fails.
     */
    private static String convertPrivateKeyToPem(PrivateKey privateKey) throws Exception {
        byte[] encoded = privateKey.getEncoded();
        String base64Encoded = Base64.getEncoder().encodeToString(encoded);
        StringBuilder sb = new StringBuilder();
        sb.append("-----BEGIN PRIVATE KEY-----\n");
        for (int i = 0; i < base64Encoded.length(); i += 64) {
            sb.append(base64Encoded, i, Math.min(i + 64, base64Encoded.length())).append("\n");
        }
        sb.append("-----END PRIVATE KEY-----");
        return sb.toString();
    }

    /**
     * Helper method to write a byte array length-prefixed into a ByteArrayOutputStream.
     * Used for OpenSSH key format conversion.
     *
     * @param baos  The ByteArrayOutputStream to write to.
     * @param bytes The byte array to write.
     * @throws IOException if an I/O error occurs.
     */
    private static void writeBytes(ByteArrayOutputStream baos, byte[] bytes) throws IOException {
        int length = bytes.length;
        baos.write((length >>> 24) & 0xFF);
        baos.write((length >>> 16) & 0xFF);
        baos.write((length >>> 8) & 0xFF);
        baos.write(length & 0xFF);
        baos.write(bytes);
    }

    /**
     * Waits for a console connection to become active and retrieves the specified detail (connection string or VNC string).
     *
     * @param connectionId The OCID of the console connection.
     * @param type         The type of detail to retrieve ("connection" for SSH, "vnc" for VNC).
     * @return The requested connection string if successful and active, null otherwise.
     */
    private String waitForConnectionAndGetDetails(String connectionId, String type) {
        for (int i = 0; i < MAX_POLLING_ATTEMPTS; i++) {
            InstanceConsoleConnection connection = getConsoleConnectionDetails(connectionId);
            if (connection != null && connection.getLifecycleState() == InstanceConsoleConnection.LifecycleState.Active) {
                if ("connection".equals(type)) {
                    return connection.getConnectionString();
                }
                if ("vnc".equals(type)) {
                    return connection.getVncConnectionString();
                }
            }
            log.debug("Waiting for console connection to become active... Attempt: {}/{}", (i + 1), MAX_POLLING_ATTEMPTS);
            try {
                TimeUnit.MILLISECONDS.sleep(POLLING_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Waiting for console connection was interrupted.");
                return null;
            }
        }
        log.warn("Timed out waiting for console connection to become active.");
        return null;
    }

    /**
     * Retrieves an OCI compute instance if it exists.
     *
     * @param instanceId The OCID of the instance.
     * @return The Instance object if found, null otherwise.
     */
    private Instance getInstanceIfExists(String instanceId) {
        try {
            GetInstanceRequest request = GetInstanceRequest.builder().instanceId(instanceId).build();
            return computeClient.getInstance(request).getInstance();
        } catch (BmcException e) {
            if (e.getStatusCode() == 404) {
                log.warn("Instance {} not found.", instanceId);
            } else {
                log.error("Failed to get instance information: {}", e.getMessage(), e);
            }
            return null;
        }
    }

    /**
     * Finds an existing active console connection for a given instance.
     *
     * @param instanceId    The OCID of the compute instance.
     * @param compartmentId The OCID of the compartment where the instance resides.
     * @return The ID of the active connection if found, null otherwise.
     */
    private String findExistingActiveConnection(String instanceId, String compartmentId) {
        try {
            ListInstanceConsoleConnectionsRequest request = ListInstanceConsoleConnectionsRequest.builder()
                    .compartmentId(compartmentId)
                    .instanceId(instanceId)
                    .build();
            ListInstanceConsoleConnectionsResponse response = computeClient.listInstanceConsoleConnections(request);
            Optional<InstanceConsoleConnection> activeConnection = response.getItems().stream()
                    .filter(conn -> conn.getLifecycleState() == InstanceConsoleConnection.LifecycleState.Active)
                    .findFirst();
            return activeConnection.map(InstanceConsoleConnection::getId).orElse(null);
        } catch (Exception e) {
            log.warn("Error while looking for existing console connections: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Waits for a console connection to be deleted.
     *
     * @param connectionId The OCID of the console connection.
     * @return true if the connection is successfully deleted or no longer exists, false if timeout.
     */
    private boolean waitForConnectionDeletion(String connectionId) {
        for (int i = 0; i < MAX_DELETION_ATTEMPTS; i++) {
            InstanceConsoleConnection connection = getConsoleConnectionDetails(connectionId);
            if (connection == null || connection.getLifecycleState() == InstanceConsoleConnection.LifecycleState.Deleted) {
                return true;
            }
            log.debug("Waiting for console connection deletion... Current State: {}, Attempt: {}/{}",
                    connection.getLifecycleState(), (i + 1), MAX_DELETION_ATTEMPTS);
            try {
                TimeUnit.MILLISECONDS.sleep(DELETION_POLLING_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Waiting for console connection deletion was interrupted.");
                return false;
            }
        }
        log.warn("Timed out waiting for console connection deletion.");
        return false;
    }

    /**
     * Validates if a console connection is currently active.
     *
     * @param connectionId The OCID of the console connection.
     * @return true if the connection is active, false otherwise.
     */
    public boolean validateConsoleConnection(String connectionId) {
        try {
            InstanceConsoleConnection connection = getConsoleConnectionDetails(connectionId);
            return (connection != null && connection.getLifecycleState() == InstanceConsoleConnection.LifecycleState.Active);
        } catch (Exception e) {
            log.error("Failed to validate console connection {}: {}", connectionId, e.getMessage(), e);
            return false;
        }
    }

}
