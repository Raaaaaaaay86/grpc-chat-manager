package com.example.grpcchatmanager.grpcService;

import com.example.chatManager.ChatManagerServiceGrpc;
import com.example.chatManager.ChatServerStreamRequest;
import com.example.chatManager.ChatServerStreamResponse;
import com.example.chatManager.GetChatServerDNSRequest;
import com.example.chatManager.GetChatServerDNSResponse;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@GrpcService
public class ChatManagerService extends ChatManagerServiceGrpc.ChatManagerServiceImplBase {

    static final Map<String, String> roomDNSMap = new HashMap<>();

    static final List<String> chatServerNameList = new ArrayList<>();

    static Integer roundRobinIndex = 0;

    Logger logger = LoggerFactory.getLogger(ChatManagerService.class);

    @Override
    public void getChatServerDNS(GetChatServerDNSRequest request, StreamObserver<GetChatServerDNSResponse> responseObserver) {
        if (request.getRoomID().isEmpty()) {
            var newRoomID = UUID.randomUUID().toString();
            var roundRobinDNS = chatServerNameList.get(roundRobinIndex);
            roomDNSMap.put(newRoomID, roundRobinDNS);

            var response = GetChatServerDNSResponse.newBuilder()
                    .setSuccess(true)
                    .setServerName(roundRobinDNS)
                    .setRoomID(newRoomID)
                    .build();

            responseObserver.onNext(response);

            if (roundRobinIndex.equals(chatServerNameList.size() - 1)) {
                roundRobinIndex = 0;
            } else {
                roundRobinIndex += 1;
            }
        } else {
            var roomDNS = roomDNSMap.get(request.getRoomID());
            var response = GetChatServerDNSResponse.newBuilder()
                    .setSuccess(true)
                    .setServerName(roomDNS)
                    .setRoomID(request.getRoomID())
                    .build();
            responseObserver.onNext(response);
        }

        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<ChatServerStreamRequest> chatSeverStream(StreamObserver<ChatServerStreamResponse> responseObserver) {
        return new StreamObserver<>() {
            String serverDNS;

            @Override
            public void onNext(ChatServerStreamRequest chatSeverStreamRequest) {
                synchronized (chatServerNameList) {
                    chatServerNameList.add(chatSeverStreamRequest.getDns());
                    logger.info("Registered a new chat server DNS: " + chatSeverStreamRequest.getDns());
                }

                serverDNS = chatSeverStreamRequest.getDns();

                var response = ChatServerStreamResponse.newBuilder()
                        .setSuccess(true)
                        .setMessage("This server has registered to ChatManager with DNS: " + chatSeverStreamRequest.getDns())
                        .build();

                responseObserver.onNext(response);
            }

            @Override
            public void onError(Throwable throwable) {
                logger.info(serverDNS + " has disconnected");
                synchronized (chatServerNameList) {
                    chatServerNameList.removeIf(dns -> dns.equalsIgnoreCase(serverDNS));
                }
            }

            @Override
            public void onCompleted() {
                logger.info(serverDNS + " has completed");
                synchronized (chatServerNameList) {
                    chatServerNameList.removeIf(dns -> dns.equalsIgnoreCase(serverDNS));
                }
            }
        };
    }
}
