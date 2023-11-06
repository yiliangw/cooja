package fwpx.mspsim;

public record PacketId(Transceiver trx, int channel, long seqNum) { }
