::deployments
kubectl get deployment/stg-ticket-ibm-backend -o yaml > deployment-stg-ticket-ibm-backend.yaml
kubectl get deployment/ticket-ibm-backend -o yaml > deployment-ticket-ibm-backend.yaml

::services
kubectl get service/stg-ticket-ibm-backend -o yaml > service-stg-ticket-ibm-backend.yaml
kubectl get service/ticket-ibm-backend -o yaml > service-ticket-ibm-backend.yaml

::pvc
kubectl get pvc/pvc-stg-ticket-ibm-backend -o yaml > pvc-stg-ticket-ibm-backend.yaml
kubectl get pvc/pvc-ticket-ibm-backend -o yaml > pvc-ticket-ibm-backend.yaml

::ingress
kubectl get ingress/stg-ticket-ibm-backend -o yaml > ingress-stg-ticket-ibm-backend.yaml
kubectl get ingress/ticket-ibm-backend -o yaml > ingress-ticket-ibm-backend.yaml
