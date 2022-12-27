var stats = {
    type: "GROUP",
name: "All Requests",
path: "",
pathFormatted: "group_missing-name-b06d1",
stats: {
    "name": "All Requests",
    "numberOfRequests": {
        "total": "19804",
        "ok": "19803",
        "ko": "1"
    },
    "minResponseTime": {
        "total": "7",
        "ok": "7",
        "ko": "3064"
    },
    "maxResponseTime": {
        "total": "3064",
        "ok": "2952",
        "ko": "3064"
    },
    "meanResponseTime": {
        "total": "22",
        "ok": "22",
        "ko": "3064"
    },
    "standardDeviation": {
        "total": "81",
        "ok": "78",
        "ko": "0"
    },
    "percentiles1": {
        "total": "14",
        "ok": "14",
        "ko": "3064"
    },
    "percentiles2": {
        "total": "17",
        "ok": "17",
        "ko": "3064"
    },
    "percentiles3": {
        "total": "27",
        "ok": "27",
        "ko": "3064"
    },
    "percentiles4": {
        "total": "285",
        "ok": "281",
        "ko": "3064"
    },
    "group1": {
    "name": "t < 800 ms",
    "htmlName": "t < 800 ms",
    "count": 19775,
    "percentage": 100
},
    "group2": {
    "name": "800 ms <= t < 1200 ms",
    "htmlName": "t ≥ 800 ms <br> t < 1200 ms",
    "count": 15,
    "percentage": 0
},
    "group3": {
    "name": "t ≥ 1200 ms",
    "htmlName": "t ≥ 1200 ms",
    "count": 13,
    "percentage": 0
},
    "group4": {
    "name": "failed",
    "htmlName": "failed",
    "count": 1,
    "percentage": 0
},
    "meanNumberOfRequestsPerSecond": {
        "total": "39.608",
        "ok": "39.606",
        "ko": "0.002"
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
        "total": "9902",
        "ok": "9902",
        "ko": "0"
    },
    "minResponseTime": {
        "total": "7",
        "ok": "7",
        "ko": "-"
    },
    "maxResponseTime": {
        "total": "2952",
        "ok": "2952",
        "ko": "-"
    },
    "meanResponseTime": {
        "total": "15",
        "ok": "15",
        "ko": "-"
    },
    "standardDeviation": {
        "total": "82",
        "ok": "82",
        "ko": "-"
    },
    "percentiles1": {
        "total": "9",
        "ok": "9",
        "ko": "-"
    },
    "percentiles2": {
        "total": "10",
        "ok": "10",
        "ko": "-"
    },
    "percentiles3": {
        "total": "18",
        "ok": "18",
        "ko": "-"
    },
    "percentiles4": {
        "total": "82",
        "ok": "82",
        "ko": "-"
    },
    "group1": {
    "name": "t < 800 ms",
    "htmlName": "t < 800 ms",
    "count": 9885,
    "percentage": 100
},
    "group2": {
    "name": "800 ms <= t < 1200 ms",
    "htmlName": "t ≥ 800 ms <br> t < 1200 ms",
    "count": 4,
    "percentage": 0
},
    "group3": {
    "name": "t ≥ 1200 ms",
    "htmlName": "t ≥ 1200 ms",
    "count": 13,
    "percentage": 0
},
    "group4": {
    "name": "failed",
    "htmlName": "failed",
    "count": 0,
    "percentage": 0
},
    "meanNumberOfRequestsPerSecond": {
        "total": "19.804",
        "ok": "19.804",
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
        "total": "9902",
        "ok": "9901",
        "ko": "1"
    },
    "minResponseTime": {
        "total": "12",
        "ok": "12",
        "ko": "3064"
    },
    "maxResponseTime": {
        "total": "3064",
        "ok": "1142",
        "ko": "3064"
    },
    "meanResponseTime": {
        "total": "30",
        "ok": "30",
        "ko": "3064"
    },
    "standardDeviation": {
        "total": "79",
        "ok": "73",
        "ko": "0"
    },
    "percentiles1": {
        "total": "17",
        "ok": "17",
        "ko": "3064"
    },
    "percentiles2": {
        "total": "19",
        "ok": "19",
        "ko": "3064"
    },
    "percentiles3": {
        "total": "40",
        "ok": "39",
        "ko": "3064"
    },
    "percentiles4": {
        "total": "514",
        "ok": "512",
        "ko": "3064"
    },
    "group1": {
    "name": "t < 800 ms",
    "htmlName": "t < 800 ms",
    "count": 9890,
    "percentage": 100
},
    "group2": {
    "name": "800 ms <= t < 1200 ms",
    "htmlName": "t ≥ 800 ms <br> t < 1200 ms",
    "count": 11,
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
    "count": 1,
    "percentage": 0
},
    "meanNumberOfRequestsPerSecond": {
        "total": "19.804",
        "ok": "19.802",
        "ko": "0.002"
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
