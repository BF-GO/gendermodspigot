package dbrighthd.wildfiregendermodplugin.networking;

import com.destroystokyo.paper.profile.PlayerProfile;
import dbrighthd.wildfiregendermodplugin.GenderModPlugin;
import dbrighthd.wildfiregendermodplugin.logging.CustomPluginLogger;
import dbrighthd.wildfiregendermodplugin.wildfire.ModConstants;
import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import io.papermc.paper.event.connection.configuration.PlayerConnectionInitialConfigureEvent;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

class Beta5HandshakeTest {
    @Test
    void beta5ChannelsUseTheReleasedModNamespace() {
        assertEquals("female_gender_mod:serverbound/hello", ModConstants.BETA5_SERVERBOUND_HELLO);
        assertEquals("female_gender_mod:clientbound/hello", ModConstants.BETA5_CLIENTBOUND_HELLO);
        assertEquals("female_gender_mod:serverbound/sync", ModConstants.BETA5_SERVERBOUND_SYNC);
        assertEquals("female_gender_mod:clientbound/sync", ModConstants.BETA5_CLIENTBOUND_SYNC);
    }

    @Test
    void advertisesConfigurationChannelBeforeClientRegistration() throws Exception {
        GenderModPlugin plugin = mock(GenderModPlugin.class);
        PaperConfigurationPayloadSender sender = mock(PaperConfigurationPayloadSender.class);
        PlayerConfigurationConnection connection = mock(PlayerConfigurationConnection.class);
        PlayerConnectionInitialConfigureEvent event = mock(PlayerConnectionInitialConfigureEvent.class);
        when(event.getConnection()).thenReturn(connection);

        new Beta5Handshake(plugin, sender).onInitialConfigure(event);

        verify(sender).send(eq(connection), eq("minecraft:register"),
                aryEq(ModConstants.BETA5_SERVERBOUND_HELLO.getBytes(StandardCharsets.US_ASCII)));
    }

    @Test
    void acceptsConfigurationReplyBeforePlayerEntersGame() throws Exception {
        GenderModPlugin plugin = mock(GenderModPlugin.class);
        PaperConfigurationPayloadSender sender = mock(PaperConfigurationPayloadSender.class);
        CustomPluginLogger logger = mock(CustomPluginLogger.class);
        SyncStateCoordinator coordinator = mock(SyncStateCoordinator.class);
        PlayerConfigurationConnection connection = mock(PlayerConfigurationConnection.class);
        PlayerProfile profile = mock(PlayerProfile.class);
        AsyncPlayerConnectionConfigureEvent event = mock(AsyncPlayerConnectionConfigureEvent.class);
        UUID id = UUID.randomUUID();
        when(plugin.getCustomLogger()).thenReturn(logger);
        when(plugin.getSyncStateCoordinator()).thenReturn(coordinator);
        when(connection.getProfile()).thenReturn(profile);
        when(profile.getId()).thenReturn(id);
        when(profile.getName()).thenReturn("viewer");
        when(connection.getListeningPluginChannels()).thenReturn(Set.of(ModConstants.BETA5_CLIENTBOUND_HELLO));
        when(event.getConnection()).thenReturn(connection);

        Beta5Handshake handshake = new Beta5Handshake(plugin, sender);
        doAnswer(invocation -> {
            handshake.onPluginMessageReceived(ModConstants.BETA5_SERVERBOUND_HELLO, connection, new byte[]{2});
            return null;
        }).when(sender).send(eq(connection), eq(ModConstants.BETA5_CLIENTBOUND_HELLO), any(byte[].class));

        handshake.onConfigure(event);

        verify(coordinator).onBeta5HelloAccepted(id);
    }

    @Test
    void staleConnectionWithSameProfileCannotCompleteCurrentHello() throws Exception {
        GenderModPlugin plugin = mock(GenderModPlugin.class);
        PaperConfigurationPayloadSender sender = mock(PaperConfigurationPayloadSender.class);
        SyncStateCoordinator coordinator = mock(SyncStateCoordinator.class);
        PlayerConfigurationConnection connection = mock(PlayerConfigurationConnection.class);
        PlayerConfigurationConnection staleConnection = mock(PlayerConfigurationConnection.class);
        PlayerProfile profile = mock(PlayerProfile.class);
        AsyncPlayerConnectionConfigureEvent event = mock(AsyncPlayerConnectionConfigureEvent.class);
        UUID id = UUID.randomUUID();
        when(plugin.getCustomLogger()).thenReturn(mock(CustomPluginLogger.class));
        when(plugin.getSyncStateCoordinator()).thenReturn(coordinator);
        when(connection.getProfile()).thenReturn(profile);
        when(staleConnection.getProfile()).thenReturn(profile);
        when(profile.getId()).thenReturn(id);
        when(connection.getListeningPluginChannels()).thenReturn(Set.of(ModConstants.BETA5_CLIENTBOUND_HELLO));
        when(event.getConnection()).thenReturn(connection);

        Beta5Handshake handshake = new Beta5Handshake(plugin, sender);
        doAnswer(invocation -> {
            // The old connection's compatible reply must not authorize this attempt.
            handshake.onPluginMessageReceived(ModConstants.BETA5_SERVERBOUND_HELLO,
                    staleConnection, new byte[]{2});
            handshake.onPluginMessageReceived(ModConstants.BETA5_SERVERBOUND_HELLO,
                    connection, new byte[]{1});
            return null;
        }).when(sender).send(eq(connection), eq(ModConstants.BETA5_CLIENTBOUND_HELLO), any(byte[].class));

        handshake.onConfigure(event);

        verify(coordinator, never()).onBeta5HelloAccepted(any());
    }

    @Test
    void skipsKnownNonFabricClientsWithoutTheConfigurationChannel() throws Exception {
        GenderModPlugin plugin = mock(GenderModPlugin.class);
        PaperConfigurationPayloadSender sender = mock(PaperConfigurationPayloadSender.class);
        when(plugin.getCustomLogger()).thenReturn(mock(CustomPluginLogger.class));
        PlayerConfigurationConnection connection = mock(PlayerConfigurationConnection.class);
        PlayerProfile profile = mock(PlayerProfile.class);
        AsyncPlayerConnectionConfigureEvent event = mock(AsyncPlayerConnectionConfigureEvent.class);
        when(connection.getProfile()).thenReturn(profile);
        when(profile.getId()).thenReturn(UUID.randomUUID());
        when(connection.getListeningPluginChannels()).thenReturn(Set.of());
        when(connection.getClientBrandName()).thenReturn("vanilla");
        when(event.getConnection()).thenReturn(connection);

        new Beta5Handshake(plugin, sender).onConfigure(event);

        verify(sender, never()).send(any(), any(), any());
    }

    @Test
    void negotiatesWithFabricEvenWithoutAdvertisedChannel() throws Exception {
        GenderModPlugin plugin = mock(GenderModPlugin.class);
        PaperConfigurationPayloadSender sender = mock(PaperConfigurationPayloadSender.class);
        CustomPluginLogger logger = mock(CustomPluginLogger.class);
        SyncStateCoordinator coordinator = mock(SyncStateCoordinator.class);
        PlayerConfigurationConnection connection = mock(PlayerConfigurationConnection.class);
        PlayerProfile profile = mock(PlayerProfile.class);
        AsyncPlayerConnectionConfigureEvent event = mock(AsyncPlayerConnectionConfigureEvent.class);
        UUID id = UUID.randomUUID();
        when(plugin.getCustomLogger()).thenReturn(logger);
        when(plugin.getSyncStateCoordinator()).thenReturn(coordinator);
        when(connection.getProfile()).thenReturn(profile);
        when(profile.getId()).thenReturn(id);
        when(connection.getListeningPluginChannels()).thenReturn(Set.of());
        when(connection.getClientBrandName()).thenReturn("fabric");
        when(event.getConnection()).thenReturn(connection);

        Beta5Handshake handshake = new Beta5Handshake(plugin, sender);
        doAnswer(invocation -> {
            handshake.onPluginMessageReceived(ModConstants.BETA5_SERVERBOUND_HELLO, connection, new byte[]{2});
            return null;
        }).when(sender).send(eq(connection), eq(ModConstants.BETA5_CLIENTBOUND_HELLO), any(byte[].class));

        handshake.onConfigure(event);

        verify(coordinator).onBeta5HelloAccepted(id);
    }
}
