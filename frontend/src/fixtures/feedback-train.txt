import json

weight = 0.0
learning_rate = 0.1
updates = 10

# Initial metrics
metrics = [{
    "step": 0,
    "weight": weight,
    "loss": (weight - 2) ** 2
}]

# Gradient descent
for step in range(1, updates + 1):
    gradient = 2 * (weight - 2)
    weight -= learning_rate * gradient
    loss = (weight - 2) ** 2
    metrics.append({
        "step": step,
        "weight": weight,
        "loss": loss
})

# Write metrics
with open('metrics.jsonl', 'w') as f:
    for metric in metrics:
        f.write(json.dumps(metric) + '\n')

# Print results
print(f"Initial loss: {metrics[0]['loss']}")
print(f"Final loss: {metrics[-1]['loss']}")