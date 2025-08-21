{{/*
Expand the name of the chart.
*/}}
{{- define "am-portfolio.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "am-portfolio.fullname" -}}
{{- default .Chart.Name .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "am-portfolio.labels" -}}
app.kubernetes.io/name: {{ include "am-portfolio.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "am-portfolio.selectorLabels" -}}
app.kubernetes.io/name: {{ include "am-portfolio.name" . }}
app: am-portfolio
{{- end }}

{{/*
Infrastructure service names
*/}}
{{- define "am-portfolio.postgresql.fullname" -}}
{{- .Values.postgresql.fullname }}
{{- end }}

{{- define "am-portfolio.influxdb.fullname" -}}
{{- .Values.influxdb.url }}
{{- end }}

{{- define "am-portfolio.kafka.fullname" -}}
{{- .Values.kafka.bootstrapServers }}
{{- end }}

{{- define "am-portfolio.zookeeper.fullname" -}}
{{- .Values.kafka.zookeeper.connect }}
{{- end }}
