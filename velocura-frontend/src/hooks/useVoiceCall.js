import { useRef, useState, useCallback } from "react";

const ICE_SERVERS = {
  iceServers: [
    { urls: "stun:stun.l.google.com:19302" },
    { urls: "stun:stun1.l.google.com:19302" },
  ]
};

export function useVoiceCall(sendCallSignal, currentUserId) {
  const [callState, setCallState] = useState("IDLE"); // IDLE | CALLING | INCOMING | ACTIVE | ENDED
  const peerRef = useRef(null);
  const localStreamRef = useRef(null);
  const remoteAudioRef = useRef(null);

  const createPeer = useCallback((targetUserId) => {
    const peer = new RTCPeerConnection(ICE_SERVERS);
    peer.onicecandidate = (e) => {
      if (e.candidate) {
        sendCallSignal("ICE_CANDIDATE", JSON.stringify(e.candidate), targetUserId);
      }
    };
    peer.ontrack = (e) => {
      if (remoteAudioRef.current && e.streams && e.streams[0]) {
        remoteAudioRef.current.srcObject = e.streams[0];
      }
    };
    return peer;
  }, [sendCallSignal]);

  const startCall = useCallback(async (toUserId) => {
    try {
      setCallState("CALLING");
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      localStreamRef.current = stream;
      const peer = createPeer(toUserId);
      stream.getTracks().forEach(t => peer.addTrack(t, stream));
      const offer = await peer.createOffer();
      await peer.setLocalDescription(offer);
      peerRef.current = peer;
      sendCallSignal("OFFER", JSON.stringify(offer), toUserId);
      sendCallSignal("CALL_STARTED", null, toUserId);
    } catch (err) {
      console.error("Error starting voice call:", err);
      setCallState("IDLE");
    }
  }, [createPeer, sendCallSignal]);

  const acceptCall = useCallback(async (incomingSignal) => {
    try {
      setCallState("ACTIVE");
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      localStreamRef.current = stream;
      const peer = createPeer(incomingSignal.fromUserId);
      stream.getTracks().forEach(t => peer.addTrack(t, stream));
      await peer.setRemoteDescription(new RTCSessionDescription(JSON.parse(incomingSignal.signal)));
      const answer = await peer.createAnswer();
      await peer.setLocalDescription(answer);
      peerRef.current = peer;
      sendCallSignal("ANSWER", JSON.stringify(answer), incomingSignal.fromUserId);
    } catch (err) {
      console.error("Error accepting voice call:", err);
      setCallState("IDLE");
    }
  }, [createPeer, sendCallSignal]);

  const handleSignal = useCallback(async (signal) => {
    const peer = peerRef.current;
    if (!peer || !signal) return;

    try {
      if (signal.type === "ANSWER") {
        await peer.setRemoteDescription(new RTCSessionDescription(JSON.parse(signal.signal)));
        setCallState("ACTIVE");
      }
      if (signal.type === "ICE_CANDIDATE") {
        await peer.addIceCandidate(new RTCIceCandidate(JSON.parse(signal.signal)));
      }
    } catch (err) {
      console.warn("Failed to handle WebRTC signal:", err);
    }
  }, []);

  const endCall = useCallback((toUserId) => {
    try {
      peerRef.current?.close();
      localStreamRef.current?.getTracks().forEach(t => t.stop());
      peerRef.current = null;
      localStreamRef.current = null;
      setCallState("ENDED");
      sendCallSignal("CALL_END", null, toUserId);
      sendCallSignal("CALL_ENDED", null, toUserId);
      setTimeout(() => setCallState("IDLE"), 1500);
    } catch (err) {
      console.error("Error ending voice call:", err);
      setCallState("IDLE");
    }
  }, [sendCallSignal]);

  return {
    callState,
    setCallState,
    remoteAudioRef,
    startCall,
    acceptCall,
    handleSignal,
    endCall
  };
}
