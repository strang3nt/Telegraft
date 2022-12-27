var stats = {
    type: "GROUP",
name: "All Requests",
path: "",
pathFormatted: "group_missing-name-b06d1",
stats: {
    "name": "All Requests",
    "numberOfRequests": {
        "total": "200",
        "ok": "100",
        "ko": "100"
    },
    "minResponseTime": {
        "total": "5",
        "ok": "19",
        "ko": "5"
    },
    "maxResponseTime": {
        "total": "3040",
        "ok": "2313",
        "ko": "3040"
    },
    "meanResponseTime": {
        "total": "684",
        "ok": "305",
        "ko": "1064"
    },
    "standardDeviation": {
        "total": "1169",
        "ok": "612",
        "ko": "1439"
    },
    "percentiles1": {
        "total": "24",
        "ok": "27",
        "ko": "7"
    },
    "percentiles2": {
        "total": "769",
        "ok": "166",
        "ko": "3023"
    },
    "percentiles3": {
        "total": "3025",
        "ok": "1933",
        "ko": "3026"
    },
    "percentiles4": {
        "total": "3033",
        "ok": "2296",
        "ko": "3036"
    },
    "group1": {
    "name": "t < 800 ms",
    "htmlName": "t < 800 ms",
    "count": 85,
    "percentage": 43
},
    "group2": {
    "name": "800 ms <= t < 1200 ms",
    "htmlName": "t ≥ 800 ms <br> t < 1200 ms",
    "count": 4,
    "percentage": 2
},
    "group3": {
    "name": "t ≥ 1200 ms",
    "htmlName": "t ≥ 1200 ms",
    "count": 11,
    "percentage": 6
},
    "group4": {
    "name": "failed",
    "htmlName": "failed",
    "count": 100,
    "percentage": 50
},
    "meanNumberOfRequestsPerSecond": {
        "total": "15.385",
        "ok": "7.692",
        "ko": "7.692"
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
        "total": "100",
        "ok": "100",
        "ko": "0"
    },
    "minResponseTime": {
        "total": "19",
        "ok": "19",
        "ko": "-"
    },
    "maxResponseTime": {
        "total": "2313",
        "ok": "2313",
        "ko": "-"
    },
    "meanResponseTime": {
        "total": "305",
        "ok": "305",
        "ko": "-"
    },
    "standardDeviation": {
        "total": "612",
        "ok": "612",
        "ko": "-"
    },
    "percentiles1": {
        "total": "27",
        "ok": "27",
        "ko": "-"
    },
    "percentiles2": {
        "total": "166",
        "ok": "166",
        "ko": "-"
    },
    "percentiles3": {
        "total": "1933",
        "ok": "1933",
        "ko": "-"
    },
    "percentiles4": {
        "total": "2296",
        "ok": "2296",
        "ko": "-"
    },
    "group1": {
    "name": "t < 800 ms",
    "htmlName": "t < 800 ms",
    "count": 85,
    "percentage": 85
},
    "group2": {
    "name": "800 ms <= t < 1200 ms",
    "htmlName": "t ≥ 800 ms <br> t < 1200 ms",
    "count": 4,
    "percentage": 4
},
    "group3": {
    "name": "t ≥ 1200 ms",
    "htmlName": "t ≥ 1200 ms",
    "count": 11,
    "percentage": 11
},
    "group4": {
    "name": "failed",
    "htmlName": "failed",
    "count": 0,
    "percentage": 0
},
    "meanNumberOfRequestsPerSecond": {
        "total": "7.692",
        "ok": "7.692",
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
        "total": "100",
        "ok": "0",
        "ko": "100"
    },
    "minResponseTime": {
        "total": "5",
        "ok": "-",
        "ko": "5"
    },
    "maxResponseTime": {
        "total": "3040",
        "ok": "-",
        "ko": "3040"
    },
    "meanResponseTime": {
        "total": "1064",
        "ok": "-",
        "ko": "1064"
    },
    "standardDeviation": {
        "total": "1439",
        "ok": "-",
        "ko": "1439"
    },
    "percentiles1": {
        "total": "7",
        "ok": "-",
        "ko": "7"
    },
    "percentiles2": {
        "total": "3023",
        "ok": "-",
        "ko": "3023"
    },
    "percentiles3": {
        "total": "3026",
        "ok": "-",
        "ko": "3026"
    },
    "percentiles4": {
        "total": "3036",
        "ok": "-",
        "ko": "3036"
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
    "count": 0,
    "percentage": 0
},
    "group3": {
    "name": "t ≥ 1200 ms",
    "htmlName": "t ≥ 1200 ms",
    "count": 0,
    "percentage": 0
},
    "group4": {
    "name": "failed",
    "htmlName": "failed",
    "count": 100,
    "percentage": 100
},
    "meanNumberOfRequestsPerSecond": {
        "total": "7.692",
        "ok": "-",
        "ko": "7.692"
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
