package com.example.whatsoff.viewmodel

import com.example.networkcore.connection.PeerConnectionManager
import com.example.whatsoff.data.MessageDao
import com.example.whatsoff.discovery.DiscoveryManager
import com.example.whatsoff.model.ChatMessage
import com.example.whatsoff.model.ConnectionState
import com.example.whatsoff.model.DeliveryStatus
import com.example.whatsoff.model.DiscoveredDevice
import com.example.whatsoff.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

/**
 * Fake in-memory MessageDao for unit testing.
 */
class FakeMessageDao : MessageDao {
    private val _messagesFlow = MutableStateFlow<List<ChatMessage>>(emptyList())
    override val messagesFlow: StateFlow<List<ChatMessage>> = _messagesFlow.asStateFlow()

    val storedMessages = mutableListOf<ChatMessage>()

    override suspend fun setActivePeer(peerAddress: String) {
        _messagesFlow.value = storedMessages.filter { it.peerAddress == peerAddress }
    }

    override suspend fun insertMessage(message: ChatMessage) {
        storedMessages.add(message)
        _messagesFlow.value = storedMessages.toList()
    }

    override suspend fun updateMessageStatus(messageId: String, status: DeliveryStatus) {
        val idx = storedMessages.indexOfFirst { it.id == messageId }
        if (idx != -1) {
            val updated = storedMessages[idx].copy(status = status)
            storedMessages[idx] = updated
            _messagesFlow.value = storedMessages.toList()
        }
    }

    override suspend fun getMessagesForPeer(peerAddress: String): List<ChatMessage> {
        return storedMessages.filter { it.peerAddress == peerAddress }
    }

    override suspend fun clearMessagesForPeer(peerAddress: String) {
        storedMessages.removeAll { it.peerAddress == peerAddress }
        _messagesFlow.value = storedMessages.toList()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testFakeMessageDaoStoresAndUpdatesDeliveryStatus() = runTest(testDispatcher) {
        val dao = FakeMessageDao()
        val msg = ChatMessage(
            id = "msg-101",
            message = "Hello test",
            isMine = true,
            status = DeliveryStatus.SENT,
            peerAddress = "192.168.1.50"
        )

        dao.insertMessage(msg)
        assertEquals(1, dao.storedMessages.size)
        assertEquals(DeliveryStatus.SENT, dao.storedMessages[0].status)

        // Simulate incoming DeliveryAck
        dao.updateMessageStatus("msg-101", DeliveryStatus.DELIVERED)
        assertEquals(DeliveryStatus.DELIVERED, dao.storedMessages[0].status)
    }

    @Test
    fun testDiscoveredDeviceSorting() {
        val dev1 = DiscoveredDevice("Device B", "192.168.1.2", isHosting = false)
        val dev2 = DiscoveredDevice("Device A", "192.168.1.3", isHosting = true)
        val dev3 = DiscoveredDevice("Device C", "192.168.1.4", isHosting = false)

        val list = listOf(dev1, dev2, dev3)
        val sorted = list.sortedWith(
            compareByDescending<DiscoveredDevice> { it.isHosting }
                .thenBy { it.name }
        )

        // Hosting devices must appear first, followed by alphabetical ordering
        assertEquals("Device A", sorted[0].name)
        assertEquals(true, sorted[0].isHosting)
        assertEquals("Device B", sorted[1].name)
        assertEquals("Device C", sorted[2].name)
    }
}
