var stats = {
    type: "GROUP",
name: "All Requests",
path: "",
pathFormatted: "group_missing-name-b06d1",
stats: {
    "name": "All Requests",
    "numberOfRequests": {
        "total": "594",
        "ok": "494",
        "ko": "100"
    },
    "minResponseTime": {
        "total": "109",
        "ok": "109",
        "ko": "2908"
    },
    "maxResponseTime": {
        "total": "3550",
        "ok": "3550",
        "ko": "3154"
    },
    "meanResponseTime": {
        "total": "1650",
        "ok": "1369",
        "ko": "3042"
    },
    "standardDeviation": {
        "total": "924",
        "ok": "746",
        "ko": "27"
    },
    "percentiles1": {
        "total": "1476",
        "ok": "1254",
        "ko": "3036"
    },
    "percentiles2": {
        "total": "2496",
        "ok": "1755",
        "ko": "3057"
    },
    "percentiles3": {
        "total": "3065",
        "ok": "2699",
        "ko": "3082"
    },
    "percentiles4": {
        "total": "3221",
        "ok": "3287",
        "ko": "3105"
    },
    "group1": {
    "name": "t < 800 ms",
    "htmlName": "t < 800 ms",
    "count": 121,
    "percentage": 20
},
    "group2": {
    "name": "800 ms <= t < 1200 ms",
    "htmlName": "t ≥ 800 ms <br> t < 1200 ms",
    "count": 112,
    "percentage": 19
},
    "group3": {
    "name": "t ≥ 1200 ms",
    "htmlName": "t ≥ 1200 ms",
    "count": 261,
    "percentage": 44
},
    "group4": {
    "name": "failed",
    "htmlName": "failed",
    "count": 100,
    "percentage": 17
},
    "meanNumberOfRequestsPerSecond": {
        "total": "42.429",
        "ok": "35.286",
        "ko": "7.143"
    }
},
contents: {
"req_user-reads-mess-c5147": {
        type: "REQUEST",
        name: "user_reads_messages",
path: "user_reads_messages",
pathFormatted: "req_user-reads-mess-c5147",
stats: {
    "name": "user_reads_messages",
    "numberOfRequests": {
        "total": "297",
        "ok": "297",
        "ko": "0"
    },
    "minResponseTime": {
        "total": "109",
        "ok": "109",
        "ko": "-"
    },
    "maxResponseTime": {
        "total": "3486",
        "ok": "3486",
        "ko": "-"
    },
    "meanResponseTime": {
        "total": "1112",
        "ok": "1112",
        "ko": "-"
    },
    "standardDeviation": {
        "total": "747",
        "ok": "747",
        "ko": "-"
    },
    "percentiles1": {
        "total": "918",
        "ok": "918",
        "ko": "-"
    },
    "percentiles2": {
        "total": "1373",
        "ok": "1373",
        "ko": "-"
    },
    "percentiles3": {
        "total": "2728",
        "ok": "2728",
        "ko": "-"
    },
    "percentiles4": {
        "total": "3398",
        "ok": "3398",
        "ko": "-"
    },
    "group1": {
    "name": "t < 800 ms",
    "htmlName": "t < 800 ms",
    "count": 121,
    "percentage": 41
},
    "group2": {
    "name": "800 ms <= t < 1200 ms",
    "htmlName": "t ≥ 800 ms <br> t < 1200 ms",
    "count": 78,
    "percentage": 26
},
    "group3": {
    "name": "t ≥ 1200 ms",
    "htmlName": "t ≥ 1200 ms",
    "count": 98,
    "percentage": 33
},
    "group4": {
    "name": "failed",
    "htmlName": "failed",
    "count": 0,
    "percentage": 0
},
    "meanNumberOfRequestsPerSecond": {
        "total": "21.214",
        "ok": "21.214",
        "ko": "-"
    }
}
    },"req_user-sends-mess-426e9": {
        type: "REQUEST",
        name: "user_sends_messages",
path: "user_sends_messages",
pathFormatted: "req_user-sends-mess-426e9",
stats: {
    "name": "user_sends_messages",
    "numberOfRequests": {
        "total": "297",
        "ok": "197",
        "ko": "100"
    },
    "minResponseTime": {
        "total": "809",
        "ok": "809",
        "ko": "2908"
    },
    "maxResponseTime": {
        "total": "3550",
        "ok": "3550",
        "ko": "3154"
    },
    "meanResponseTime": {
        "total": "2189",
        "ok": "1756",
        "ko": "3042"
    },
    "standardDeviation": {
        "total": "756",
        "ok": "552",
        "ko": "27"
    },
    "percentiles1": {
        "total": "2192",
        "ok": "1644",
        "ko": "3036"
    },
    "percentiles2": {
        "total": "3027",
        "ok": "2156",
        "ko": "3057"
    },
    "percentiles3": {
        "total": "3069",
        "ok": "2691",
        "ko": "3082"
    },
    "percentiles4": {
        "total": "3093",
        "ok": "2973",
        "ko": "3105"
    },
    "group1": {
    "name": "t < 800 ms",
    "htmlName": "t < 800 ms",
    "count": 0,
    "percentage": 0
},
    "group2": {
    "name": "800 ms <= t < 1200 ms",
    "htmlName": "t ≥ 800 ms <br> t < 1200 ms",
    "count": 34,
    "percentage": 11
},
    "group3": {
    "name": "t ≥ 1200 ms",
    "htmlName": "t ≥ 1200 ms",
    "count": 163,
    "percentage": 55
},
    "group4": {
    "name": "failed",
    "htmlName": "failed",
    "count": 100,
    "percentage": 34
},
    "meanNumberOfRequestsPerSecond": {
        "total": "21.214",
        "ok": "14.071",
        "ko": "7.143"
    }
}
    }
}

}

function fillStats(stat){
    $("#numberOfRequests").append(stat.numberOfRequests.total);
    $("#numberOfRequestsOK").append(stat.numberOfRequests.ok);
    $("#numberOfRequestsKO").append(stat.numberOfRequests.ko);

    $("#minResponseTime").append(stat.minResponseTime.total);
    $("#minResponseTimeOK").append(stat.minResponseTime.ok);
    $("#minResponseTimeKO").append(stat.minResponseTime.ko);

    $("#maxResponseTime").append(stat.maxResponseTime.total);
    $("#maxResponseTimeOK").append(stat.maxResponseTime.ok);
    $("#maxResponseTimeKO").append(stat.maxResponseTime.ko);

    $("#meanResponseTime").append(stat.meanResponseTime.total);
    $("#meanResponseTimeOK").append(stat.meanResponseTime.ok);
    $("#meanResponseTimeKO").append(stat.meanResponseTime.ko);

    $("#standardDeviation").append(stat.standardDeviation.total);
    $("#standardDeviationOK").append(stat.standardDeviation.ok);
    $("#standardDeviationKO").append(stat.standardDeviation.ko);

    $("#percentiles1").append(stat.percentiles1.total);
    $("#percentiles1OK").append(stat.percentiles1.ok);
    $("#percentiles1KO").append(stat.percentiles1.ko);

    $("#percentiles2").append(stat.percentiles2.total);
    $("#percentiles2OK").append(stat.percentiles2.ok);
    $("#percentiles2KO").append(stat.percentiles2.ko);

    $("#percentiles3").append(stat.percentiles3.total);
    $("#percentiles3OK").append(stat.percentiles3.ok);
    $("#percentiles3KO").append(stat.percentiles3.ko);

    $("#percentiles4").append(stat.percentiles4.total);
    $("#percentiles4OK").append(stat.percentiles4.ok);
    $("#percentiles4KO").append(stat.percentiles4.ko);

    $("#meanNumberOfRequestsPerSecond").append(stat.meanNumberOfRequestsPerSecond.total);
    $("#meanNumberOfRequestsPerSecondOK").append(stat.meanNumberOfRequestsPerSecond.ok);
    $("#meanNumberOfRequestsPerSecondKO").append(stat.meanNumberOfRequestsPerSecond.ko);
}
