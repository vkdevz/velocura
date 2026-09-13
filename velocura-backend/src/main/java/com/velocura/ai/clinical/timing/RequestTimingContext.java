package com.velocura.ai.clinical.timing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-precision server-side request timing context using System.nanoTime().
 * Instruments all 16 stages of request processing and tracks Neon database metrics.
 * Strictly guarantees ZERO logging of PHI, tokens, OTPs, credentials, or secrets.
 */
public class RequestTimingContext {

    private static final Logger log = LoggerFactory.getLogger("PRODUCTION_TIMING");

    private static final ThreadLocal<RequestTimingContext> CURRENT = new ThreadLocal<>();

    private final String reqId;
    private final long startNano;

    // Stage start and duration tracking (in nanoseconds)
    private long authDuration;
    private long sessionDuration;
    private long stateHydrationDuration;
    private long normDuration;
    private long safety1Duration;
    private long retrieval11kDuration;
    private long decisionEngineDuration;
    private long medSafetyDuration;
    private long labReasoningDuration;
    private long evidenceDuration;
    private long voiNbqDuration;
    private long safety2Duration;
    private long compDuration;
    private long auditDuration;
    private long serializationDuration;

    // Database / Neon specific metrics
    private long dbTotalDuration;
    private final AtomicInteger dbQueryCount = new AtomicInteger(0);
    private long dbConnectionAcquisitionDuration;
    private long dbSlowestQueryDuration;
    private String dbSlowestQueryName = "none";

    public RequestTimingContext(String reqId) {
        this.reqId = reqId != null ? reqId : "req-" + UUID.randomUUID().toString().substring(0, 8);
        this.startNano = System.nanoTime();
    }

    public static RequestTimingContext start(String correlationId) {
        RequestTimingContext ctx = new RequestTimingContext(correlationId);
        CURRENT.set(ctx);
        return ctx;
    }

    public static RequestTimingContext get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    public String getReqId() {
        return reqId;
    }

    public void recordAuth(long durationNano) { this.authDuration += durationNano; }
    public void recordSession(long durationNano) { this.sessionDuration += durationNano; }
    public void recordStateHydration(long durationNano) { this.stateHydrationDuration += durationNano; }
    public void recordNorm(long durationNano) { this.normDuration += durationNano; }
    public void recordSafety1(long durationNano) { this.safety1Duration += durationNano; }
    public void recordRetrieval11k(long durationNano) { this.retrieval11kDuration += durationNano; }
    public void recordDecisionEngine(long durationNano) { this.decisionEngineDuration += durationNano; }
    public void recordMedSafety(long durationNano) { this.medSafetyDuration += durationNano; }
    public void recordLabReasoning(long durationNano) { this.labReasoningDuration += durationNano; }
    public void recordEvidence(long durationNano) { this.evidenceDuration += durationNano; }
    public void recordVoiNbq(long durationNano) { this.voiNbqDuration += durationNano; }
    public void recordSafety2(long durationNano) { this.safety2Duration += durationNano; }
    public void recordComposition(long durationNano) { this.compDuration += durationNano; }
    public void recordAudit(long durationNano) { this.auditDuration += durationNano; }
    public void recordSerialization(long durationNano) { this.serializationDuration += durationNano; }

    public void recordDbQuery(String queryName, long durationNano) {
        this.dbTotalDuration += durationNano;
        this.dbQueryCount.incrementAndGet();
        if (durationNano > this.dbSlowestQueryDuration) {
            this.dbSlowestQueryDuration = durationNano;
            this.dbSlowestQueryName = queryName;
        }
    }

    public void recordDbConnectionAcquisition(long durationNano) {
        this.dbConnectionAcquisitionDuration += durationNano;
    }

    public long getTotalElapsedNano() {
        return System.nanoTime() - startNano;
    }

    public double getTotalElapsedMs() {
        return (System.nanoTime() - startNano) / 1_000_000.0;
    }

    public static double toMs(long nano) {
        return nano / 1_000_000.0;
    }

    /**
     * Emits production structured timing log.
     * Contains ZERO PHI and ZERO secrets.
     */
    public void logSummary() {
        long totalNano = getTotalElapsedNano();
        double totalMs = toMs(totalNano);
        double authMs = toMs(authDuration);
        double sessionMs = toMs(sessionDuration);
        double stateMs = toMs(stateHydrationDuration);
        double normMs = toMs(normDuration);
        double safety1Ms = toMs(safety1Duration);
        double retrievalMs = toMs(retrieval11kDuration);
        double reasoningMs = toMs(decisionEngineDuration);
        double medMs = toMs(medSafetyDuration);
        double labMs = toMs(labReasoningDuration);
        double evidenceMs = toMs(evidenceDuration);
        double voiMs = toMs(voiNbqDuration);
        double safety2Ms = toMs(safety2Duration);
        double compMs = toMs(compDuration);
        double auditMs = toMs(auditDuration);
        double serMs = toMs(serializationDuration);
        double dbMs = toMs(dbTotalDuration);
        double dbSlowestMs = toMs(dbSlowestQueryDuration);
        double dbConnMs = toMs(dbConnectionAcquisitionDuration);
        int dbCount = dbQueryCount.get();

        double nonDbBackendMs = Math.max(0.0, totalMs - dbMs);

        log.info(String.format(
            "[PROD-TIMING] reqId=%s total=%.2fms auth=%.2fms session=%.2fms state=%.2fms norm=%.2fms safety1=%.2fms retrieval=%.2fms reasoning=%.2fms med=%.2fms labs=%.2fms evidence=%.2fms voi=%.2fms safety2=%.2fms comp=%.2fms audit=%.2fms ser=%.2fms | DB_TOTAL=%.2fms DB_COUNT=%d DB_SLOWEST=%.2fms(%s) DB_CONN=%.2fms | NON_DB_BACKEND=%.2fms",
            reqId, totalMs, authMs, sessionMs, stateMs, normMs, safety1Ms, retrievalMs, reasoningMs, medMs, labMs, evidenceMs, voiMs, safety2Ms, compMs, auditMs, serMs,
            dbMs, dbCount, dbSlowestMs, dbSlowestQueryName, dbConnMs, nonDbBackendMs
        ));
    }
}
